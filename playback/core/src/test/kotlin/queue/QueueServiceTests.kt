package org.jellyfin.playback.core.queue

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jellyfin.playback.core.PlayerState
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.backend.BackendService
import org.jellyfin.playback.core.backend.PlayerBackendEventListener
import org.jellyfin.playback.core.mediastream.PlayableMediaStream
import org.jellyfin.playback.core.model.PlaybackOrder
import org.jellyfin.playback.core.model.RepeatMode
import org.jellyfin.playback.core.queue.supplier.QueueSupplier

class QueueServiceTests : FunSpec({
	fun fixture(): Pair<QueueService, Job> {
		val queue = spyk(QueueService())
		coEvery { queue.onInitialize() } returns Unit
		val state = mockk<PlayerState>(relaxed = true)
		every { state.repeatMode } returns MutableStateFlow(RepeatMode.NONE)
		val job = Job()
		queue.initialize(mockk(relaxed = true), state, job)
		return queue to job
	}
	suspend fun QueueService.load(items: List<QueueEntry>) {
		addSupplier(object : QueueSupplier {
			override val size = items.size
			override suspend fun getItem(index: Int) = items.getOrNull(index)
		})
		withTimeout(5_000) { entry.first { it == items.first() } }
	}
	test("a supplier completing after clear cannot resurrect the removed queue") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val started = CompletableDeferred<Unit>()
				val release = CompletableDeferred<Unit>()
				val stale = QueueEntry()
				queue.addSupplier(object : QueueSupplier {
					override val size = 1
					override suspend fun getItem(index: Int): QueueEntry? {
						started.complete(Unit)
						release.await()
						return if (index == 0) stale else null
					}
				})
				withTimeout(5_000) { started.await() }
				val pending = job.children.toList()
				queue.clear()
				release.complete(Unit)
				withTimeout(5_000) { pending.forEach { it.join() } }
				queue.entry.value shouldBe null
				queue.entries.value shouldBe emptyList()
				queue.estimatedSize shouldBe 0
			} finally { job.cancel() }
		}
	}

	test("a canceled track load cannot add a phantom Previous entry") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val items = List(2) { QueueEntry() }
				val started = CompletableDeferred<Unit>()
				queue.addSupplier(object : QueueSupplier {
					override val size = items.size
					override suspend fun getItem(index: Int): QueueEntry? {
						if (index == 1) { started.complete(Unit); CompletableDeferred<Unit>().await() }
						return items.getOrNull(index)
					}
				})
				withTimeout(5_000) { queue.entry.first { it == items[0] } }
				val pending = async { queue.setIndex(1, saveHistory = true) }
				withTimeout(5_000) { started.await() }
				pending.cancelAndJoin()
				queue.entry.value shouldBe items[0]
				queue.peekPrevious() shouldBe null
			} finally { job.cancel() }
		}
	}

	test("a stale supplier cannot contaminate a newly loaded replacement queue") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val started = CompletableDeferred<Unit>()
				val release = CompletableDeferred<Unit>()
				queue.addSupplier(object : QueueSupplier {
					override val size = 1
					override suspend fun getItem(index: Int): QueueEntry? {
						started.complete(Unit)
						release.await()
						return QueueEntry()
					}
				})
				withTimeout(5_000) { started.await() }
				val pending = job.children.toList()
				queue.clear()
				val fresh = List(2) { QueueEntry() }
				queue.load(fresh)
				release.complete(Unit)
				withTimeout(5_000) { pending.forEach { it.join() } }
				queue.entry.value shouldBe fresh[0]
				queue.next() shouldBe fresh[1]
				queue.entries.value shouldBe fresh
				queue.estimatedSize shouldBe 2
			} finally { job.cancel() }
		}
	}

	test("clearing removed entries restores the size of a replacement queue") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val first = List(3) { QueueEntry() }
				queue.load(first)
				queue.peekNext(2)
				queue.removeEntry(first[2])
				queue.estimatedSize shouldBe 2
				queue.clear()
				queue.load(List(3) { QueueEntry() })
				queue.estimatedSize shouldBe 3
				queue.peekNext(10).size shouldBe 2
			} finally { job.cancel() }
		}
	}
	test("a late completion from an old stream cannot advance a replacement queue") {
		runBlocking {
			val ready = CompletableDeferred<PlayerBackendEventListener>()
			val backend = mockk<BackendService> {
				every { addListener(any()) } answers { ready.complete(firstArg()); Unit }
			}
			val manager = mockk<PlaybackManager> { every { backendService } returns backend }
			val state = mockk<PlayerState>(relaxed = true) {
				every { playbackOrder } returns MutableStateFlow(PlaybackOrder.DEFAULT)
				every { repeatMode } returns MutableStateFlow(RepeatMode.NONE)
			}
			val queue = QueueService()
			val job = Job()
			queue.initialize(manager, state, job)
			try {
				val listener = withTimeout(5_000) { ready.await() }
				val old = QueueEntry()
				queue.load(listOf(old))
				queue.clear()
				val replacement = List(2) { QueueEntry() }
				queue.load(replacement)
				val existing = job.children.toSet()
				listener.onMediaStreamEnd(mockk<PlayableMediaStream> { every { queueEntry } returns old })
				withTimeout(5_000) { job.children.filterNot { it in existing }.toList().forEach { it.join() } }
				queue.entry.value shouldBe replacement[0]
			} finally { job.cancel() }
		}
	}

	test("an automatic advance cannot restart playback after the user stops during its fetch") {
		runBlocking {
			val ready = CompletableDeferred<PlayerBackendEventListener>()
			val backend = mockk<BackendService> { every { addListener(any()) } answers { ready.complete(firstArg()); Unit } }
			val manager = mockk<PlaybackManager> { every { backendService } returns backend }
			val state = mockk<PlayerState>(relaxed = true) {
				every { playbackOrder } returns MutableStateFlow(PlaybackOrder.DEFAULT)
				every { repeatMode } returns MutableStateFlow(RepeatMode.NONE)
			}
			val queue = QueueService()
			val job = Job()
			queue.initialize(manager, state, job)
			try {
				val listener = withTimeout(5_000) { ready.await() }
				val first = QueueEntry()
				val started = CompletableDeferred<Unit>()
				val release = CompletableDeferred<Unit>()
				queue.addSupplier(object : QueueSupplier {
					override val size = 2
					override suspend fun getItem(index: Int): QueueEntry? {
						if (index == 1) { started.complete(Unit); release.await(); return QueueEntry() }
						return first.takeIf { index == 0 }
					}
				})
				withTimeout(5_000) { queue.entry.first { it == first } }
				val existing = job.children.toSet()
				listener.onMediaStreamEnd(mockk<PlayableMediaStream> { every { queueEntry } returns first })
				withTimeout(5_000) { started.await() }
				val pending = job.children.filterNot { it in existing }.toList()
				queue.setIndex(Queue.INDEX_NONE)
				release.complete(Unit)
				withTimeout(5_000) { pending.forEach { it.join() } }
				queue.entry.value shouldBe null
				queue.entryIndex.value shouldBe Queue.INDEX_NONE
			} finally { job.cancel() }
		}
	}

	test("concurrent peeks cannot cache the same supplier slot twice") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val items = List(3) { QueueEntry() }
				val started = CompletableDeferred<Unit>()
				val release = CompletableDeferred<Unit>()
				queue.addSupplier(object : QueueSupplier {
					override val size = items.size
					override suspend fun getItem(index: Int): QueueEntry? {
						if (index == 1) { started.complete(Unit); release.await() }
						return items.getOrNull(index)
					}
				})
				withTimeout(5_000) { queue.entry.first { it == items[0] } }
				coroutineScope {
					val first = async { queue.peekNext() }
					started.await()
					val second = async(start = CoroutineStart.UNDISPATCHED) { queue.peekNext() }
					release.complete(Unit)
					first.await() shouldBe items[1]
					second.await() shouldBe items[1]
				}
				queue.setIndex(2) shouldBe items[2]
				queue.entries.value shouldBe items
			} finally { job.cancel() }
		}
	}
	test("removing the final active track clears the active entry") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val item = QueueEntry()
				queue.load(listOf(item))
				queue.removeEntry(item)
				queue.entry.value shouldBe null
				queue.entryIndex.value shouldBe Queue.INDEX_NONE
				queue.estimatedSize shouldBe 0
			} finally { job.cancel() }
		}
	}
	test("removing the active middle track cannot duplicate Previous history") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val items = List(3) { QueueEntry() }
				queue.load(items)
				queue.next() shouldBe items[1]
				queue.removeEntry(items[1])
				queue.entry.value shouldBe items[2]
				queue.previous() shouldBe items[0]
				queue.previous() shouldBe null
			} finally { job.cancel() }
		}
	}
	test("nonpositive queue peek requests return nothing in every repeat mode") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				for (mode in RepeatMode.entries) {
					every { queue.state.repeatMode } returns MutableStateFlow(mode)
					queue.peekNext(0, useRepeatMode = true).size shouldBe 0
					queue.peekNext(-1, useRepeatMode = true).size shouldBe 0
				}
			} finally { job.cancel() }
		}
	}
})
