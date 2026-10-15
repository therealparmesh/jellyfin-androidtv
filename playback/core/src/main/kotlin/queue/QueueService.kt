// Modified for the Parmesh fork on 2026-10-15. See FORK.md.
package org.jellyfin.playback.core.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.backend.PlayerBackendEventListener
import org.jellyfin.playback.core.mediastream.PlayableMediaStream
import org.jellyfin.playback.core.model.PlaybackOrder
import org.jellyfin.playback.core.model.RepeatMode
import org.jellyfin.playback.core.plugin.PlayerService
import org.jellyfin.playback.core.queue.order.DefaultOrderIndexProvider
import org.jellyfin.playback.core.queue.order.OrderIndexProvider
import org.jellyfin.playback.core.queue.order.RandomOrderIndexProvider
import org.jellyfin.playback.core.queue.order.ShuffleOrderIndexProvider
import org.jellyfin.playback.core.queue.supplier.QueueSupplier
import kotlin.math.max

class QueueService internal constructor() : PlayerService(), Queue {
	private val suppliers = mutableListOf<QueueSupplier>()
	private var currentSupplierIndex = 0
	private var currentSupplierEntryIndex = 0
	private val fetchedEntries: MutableList<QueueEntry> = mutableListOf()
	private var removedEntries = 0
	@Volatile private var generation = 0L
	private var selectionVersion = 0L

	private val defaultOrderIndexProvider = DefaultOrderIndexProvider()
	private var orderIndexProvider: OrderIndexProvider = defaultOrderIndexProvider
	private var currentQueueIndicesPlayed = mutableListOf<Int>()

	override val estimatedSize get() = synchronized(this) { max(fetchedEntries.size, suppliers.sumOf { it.size } - removedEntries) }

	private val _entryIndex = MutableStateFlow(Queue.INDEX_NONE)
	override val entryIndex: StateFlow<Int> get() = _entryIndex.asStateFlow()

	private val _entry = MutableStateFlow<QueueEntry?>(null)
	override val entry: StateFlow<QueueEntry?> get() = _entry.asStateFlow()

	private val _entries = MutableStateFlow<List<QueueEntry>>(emptyList())
	override val entries: StateFlow<List<QueueEntry>> get() = _entries.asStateFlow()

	override suspend fun onInitialize() {
		// Reset calculated next-up indices when playback order changes
		state.playbackOrder.onEach { playbackOrder ->
			synchronized(this) {
				orderIndexProvider = when (playbackOrder) {
					PlaybackOrder.DEFAULT -> defaultOrderIndexProvider
					PlaybackOrder.RANDOM -> RandomOrderIndexProvider()
					PlaybackOrder.SHUFFLE -> ShuffleOrderIndexProvider()
				}
			}
		}.launchIn(coroutineScope)

		// Automatically advance when current stream ends
		manager.backendService.addListener(object : PlayerBackendEventListener() {
			override fun onMediaStreamEnd(mediaStream: PlayableMediaStream) {
				coroutineScope.launch {
					val eventState = synchronized(this@QueueService) {
						if (_entry.value !== mediaStream.queueEntry) return@launch
						generation to selectionVersion
					}
					val nextEntry = next(true, true, eventState.first, eventState.second)
					if (nextEntry == null) setIndex(Queue.INDEX_NONE, true, eventState.first, eventState.second)
				}
			}
		})
	}

	// Entry management

	override fun addSupplier(supplier: QueueSupplier) = synchronized(this) {
		suppliers.add(supplier)

		if (_entryIndex.value == Queue.INDEX_NONE) {
			val currentGeneration = generation
			val currentSelection = selectionVersion
			coroutineScope.launch { setIndex(0, false, currentGeneration, currentSelection) }
		}
	}

	override fun getSuppliers(): Collection<QueueSupplier> = synchronized(this) { suppliers.toList() }

	private suspend fun getOrSupplyEntry(index: Int, expectedGeneration: Long = generation): QueueEntry? {
		// Fetch additional entries from suppliers until we reach the desired index
		var entriesChanged = false
		try {
			while (true) {
				val (supplier, supplierIndex, entryIndex) = synchronized(this) {
					if (generation != expectedGeneration) return null
					if (index < fetchedEntries.size || currentSupplierIndex >= suppliers.size) {
						return fetchedEntries.getOrNull(index)
					}
					Triple(suppliers[currentSupplierIndex], currentSupplierIndex, currentSupplierEntryIndex)
				}
				val nextEntry = supplier.getItem(entryIndex)
				synchronized(this) {
					if (generation != expectedGeneration) return null
					// A concurrent peek can have fetched this same supplier slot while we suspended.
					if (currentSupplierIndex == supplierIndex && currentSupplierEntryIndex == entryIndex) {
						if (nextEntry != null) {
							fetchedEntries.add(nextEntry)
							entriesChanged = true
							currentSupplierEntryIndex++
						} else {
							currentSupplierIndex++
							currentSupplierEntryIndex = 0
						}
					}
				}
			}
		} finally {
			// Publish once per fetch, including partial progress before cancellation.
			synchronized(this) {
				if (entriesChanged && generation == expectedGeneration) _entries.value = fetchedEntries.toList()
			}
		}
	}

	override fun indexOf(entry: QueueEntry): Int? = synchronized(this) { fetchedEntries.indexOf(entry).takeIf { it != -1 } }

	override suspend fun removeEntry(entry: QueueEntry) {
		val replacement = synchronized(this) {
			val index = indexOf(entry) ?: return

			// Add to removed list
			removedEntries++

			// Remove from fetched list
			fetchedEntries.removeAt(index)

			// Update indices
			currentQueueIndicesPlayed.removeAll { it == index }
			currentQueueIndicesPlayed.replaceAll { if (it > index) it - 1 else it }
			if (_entryIndex.value >= index) _entryIndex.value -= 1

			orderIndexProvider.notifyRemoved(index)

			_entries.value = fetchedEntries.toList()
			// Play next entry if the removed entry is currently the active one.
			if (_entry.value == entry) {
				// The removed item cannot be saved to Previous history. In shuffle/random order
				// there is no active index to exclude; in default order resume at the removed slot.
				val currentIndex = if (orderIndexProvider === defaultOrderIndexProvider) _entryIndex.value else Queue.INDEX_NONE
				val nextIndex = orderIndexProvider.provideIndices(1, estimatedSize, currentQueueIndicesPlayed, currentIndex).firstOrNull()
				if (nextIndex != null) orderIndexProvider.useNextIndex()
				(nextIndex ?: Queue.INDEX_NONE) to generation
			} else null
		}
		if (replacement != null) setIndex(replacement.first, false, replacement.second)
	}

	override fun clear() = synchronized(this) {
		generation++
		selectionVersion++
		suppliers.clear()
		currentSupplierIndex = 0
		currentSupplierEntryIndex = 0
		removedEntries = 0
		defaultOrderIndexProvider.reset()
		orderIndexProvider.reset()
		fetchedEntries.clear()
		_entries.value = emptyList()
		_entry.value = null
		_entryIndex.value = Queue.INDEX_NONE
		currentQueueIndicesPlayed.clear()
	}

	// Preloading

	private fun getNextIndices(amount: Int, usePlaybackOrder: Boolean, useRepeatMode: Boolean): Collection<Int> = synchronized(this) {
		if (amount <= 0) return emptyList()
		val provider = if (usePlaybackOrder) orderIndexProvider else defaultOrderIndexProvider
		val repeatMode = if (useRepeatMode && _entryIndex.value != Queue.INDEX_NONE) state.repeatMode.value else RepeatMode.NONE

		when (repeatMode) {
			RepeatMode.NONE -> provider.provideIndices(amount, estimatedSize, currentQueueIndicesPlayed, _entryIndex.value)

			RepeatMode.REPEAT_ENTRY_ONCE -> buildList(amount) {
				add(_entryIndex.value)
				addAll(provider.provideIndices(amount - 1, estimatedSize, currentQueueIndicesPlayed, _entryIndex.value))
			}.take(amount)

			RepeatMode.REPEAT_ENTRY_INFINITE -> List(amount) { _entryIndex.value }
		}
	}

	// Jumping

	override suspend fun previous(): QueueEntry? {
		val previous = synchronized(this) { currentQueueIndicesPlayed.removeLastOrNull()?.let { it to generation } } ?: return null
		return setIndex(previous.first, false, previous.second)
	}

	override suspend fun next(usePlaybackOrder: Boolean, useRepeatMode: Boolean): QueueEntry? =
		next(usePlaybackOrder, useRepeatMode, generation)

	private suspend fun next(
		usePlaybackOrder: Boolean,
		useRepeatMode: Boolean,
		expectedGeneration: Long,
		expectedSelection: Long? = null,
	): QueueEntry? {
		val (index, selection) = synchronized(this) {
			if (generation != expectedGeneration || (expectedSelection != null && selectionVersion != expectedSelection)) return null
			val index = getNextIndices(1, usePlaybackOrder, useRepeatMode).firstOrNull() ?: return null

			val provider = if (usePlaybackOrder) orderIndexProvider else defaultOrderIndexProvider
			val repeatMode = if (useRepeatMode) state.repeatMode.value else RepeatMode.NONE

			// Automatically set repeat mode back to none when using the ONCE option
			if (repeatMode == RepeatMode.REPEAT_ENTRY_ONCE && index == this._entryIndex.value) {
				state.setRepeatMode(RepeatMode.NONE)
			} else if (repeatMode == RepeatMode.NONE) {
				provider.useNextIndex()
			}

			index to selectionVersion
		}
		return setIndex(index, true, expectedGeneration, selection)
	}

	override suspend fun setIndex(index: Int, saveHistory: Boolean): QueueEntry? = setIndex(index, saveHistory, generation)

	private suspend fun setIndex(
		index: Int,
		saveHistory: Boolean,
		expectedGeneration: Long,
		expectedSelection: Long? = null,
	): QueueEntry? {
		if (index < 0 && index != Queue.INDEX_NONE) return null
		val selection = synchronized(this) {
			if (generation != expectedGeneration || (expectedSelection != null && selectionVersion != expectedSelection)) return null
			++selectionVersion
		}
		val currentEntry = getOrSupplyEntry(index, expectedGeneration)
		return synchronized(this) {
			if (generation != expectedGeneration || selectionVersion != selection) return null
			// Only commit history after a successful load in the same queue generation.
			if (saveHistory && _entryIndex.value != Queue.INDEX_NONE) currentQueueIndicesPlayed.add(_entryIndex.value)
			_entryIndex.value = if (currentEntry == null) Queue.INDEX_NONE else index
			_entry.value = currentEntry
			currentEntry
		}
	}

	// Peeking

	override suspend fun peekPrevious(): QueueEntry? {
		val previous = synchronized(this) { currentQueueIndicesPlayed.lastOrNull()?.let { it to generation } } ?: return null
		return getOrSupplyEntry(previous.first, previous.second)
	}

	override suspend fun peekNext(
		usePlaybackOrder: Boolean,
		useRepeatMode: Boolean,
	): QueueEntry? = peekNext(1, usePlaybackOrder, useRepeatMode).firstOrNull()

	override suspend fun peekNext(
		amount: Int,
		usePlaybackOrder: Boolean,
		useRepeatMode: Boolean,
	): Collection<QueueEntry> {
		val (indices, expectedGeneration) = synchronized(this) { getNextIndices(amount, usePlaybackOrder, useRepeatMode) to generation }
		return indices.mapNotNull { index -> getOrSupplyEntry(index, expectedGeneration) }
	}
}

val PlaybackManager.queue: Queue get() = requireNotNull(getService<QueueService>())
