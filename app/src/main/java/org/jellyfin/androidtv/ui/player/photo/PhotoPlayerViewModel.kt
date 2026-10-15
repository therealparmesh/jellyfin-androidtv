// Modified for the Parmesh fork on 2026-10-13. See FORK.md.
package org.jellyfin.androidtv.ui.player.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class PhotoPlayerViewModel(
	private val api: ApiClient,
	private val userPreferences: UserPreferences,
) : ViewModel() {
	private companion object {
		const val MIN_PRESENTATION_DELAY = 4_000L
		const val MAX_PRESENTATION_DELAY = 120_000L
	}
	private var album: List<BaseItemDto> = emptyList()
	private var albumIndex = -1

	private val _currentItem = MutableStateFlow<BaseItemDto?>(null)
	val currentItem = _currentItem.asStateFlow()

	private val _settingsVisible = MutableStateFlow(false)
	val settingsVisible = _settingsVisible.asStateFlow()

	suspend fun loadItem(id: UUID, sortBy: Collection<ItemSortBy>, sortOrder: SortOrder) {
		album = emptyList()
		albumIndex = -1
		// Load requested item
		val itemResponse = withContext(Dispatchers.IO) {
			api.userLibraryApi.getItem(itemId = id).content
		}
		_currentItem.value = itemResponse

		val parentId = itemResponse.parentId
		album = if (parentId == null) emptyList() else try {
			withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					parentId = parentId,
					includeItemTypes = setOf(BaseItemKind.PHOTO),
					fields = ItemRepository.browseFields,
					sortBy = sortBy,
					sortOrder = listOf(sortOrder),
				).content.items
			}
		} catch (error: ApiClientException) {
			Timber.w(error, "Unable to load the photo album")
			emptyList()
		}
		// A selected image can be absent from a nonempty album as well as from an empty one.
		if (album.none { it.id == id }) album = listOf(itemResponse) + album
		albumIndex = album.indexOfFirst { it.id == id }
	}

	// Album actions

	fun showNext() {
		advancePhoto(1)
		restartPresentation()
	}

	fun showPrevious() {
		advancePhoto(-1)
		restartPresentation()
	}

	private fun advancePhoto(direction: Int) {
		if (album.isEmpty()) return
		albumIndex = Math.floorMod(albumIndex + direction, album.size)
		_currentItem.value = album[albumIndex]
	}

	// Presentation

	private var presentationJob: Job? = null
	private val _presentationActive = MutableStateFlow(false)
	val presentationActive = _presentationActive.asStateFlow()

	private fun createPresentationJob() = viewModelScope.launch {
		val photoPlayerPresentationDelay = userPreferences[UserPreferences.photoPlayerPresentationDelay]
			.coerceIn(MIN_PRESENTATION_DELAY, MAX_PRESENTATION_DELAY).milliseconds

		while (isActive) {
			delay(photoPlayerPresentationDelay)
			advancePhoto(1)
		}
	}

	fun startPresentation() {
		if (presentationActive.value) return
		_presentationActive.value = true

		restartPresentation()
	}

	fun restartPresentation() {
		if (!presentationActive.value || settingsVisible.value) return

		presentationJob?.cancel()
		presentationJob = createPresentationJob()
	}

	fun stopPresentation() {
		if (!presentationActive.value) return

		presentationJob?.cancel()
		presentationJob = null
		_presentationActive.value = false
	}

	fun togglePresentation() {
		if (presentationActive.value) stopPresentation()
		else startPresentation()
	}

	fun setSettingsVisible(visible: Boolean) {
		_settingsVisible.value = visible

		// Make sure presentation is stopped when settings are shown
		// but keep the _presentationActive state
		if (visible) {
			presentationJob?.cancel()
			presentationJob = null
		} else {
			restartPresentation()
		}
	}
}
