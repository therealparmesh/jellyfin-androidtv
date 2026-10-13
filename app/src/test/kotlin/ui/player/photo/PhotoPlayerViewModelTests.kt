package org.jellyfin.androidtv.ui.player.photo

import androidx.lifecycle.ViewModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

private class PhotoFixture {
	val itemsApi = mockk<ItemsApi>()
	val libraryApi = mockk<UserLibraryApi>()
	val preferences = mockk<UserPreferences>()
	val api = mockk<ApiClient> {
		every { getOrCreateApi(ItemsApi::class, any()) } returns itemsApi
		every { getOrCreateApi(UserLibraryApi::class, any()) } returns libraryApi
	}
	val model = PhotoPlayerViewModel(api, preferences)
	val store = ViewModelStore().apply { put("photo", model) }
	val parentId = UUID.randomUUID()
	val photos = List(3) { BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.PHOTO, parentId = parentId) }
	init { every { preferences[UserPreferences.photoPlayerPresentationDelay] } returns 8_000L }
	suspend fun load(item: BaseItemDto = photos.first(), album: List<BaseItemDto> = photos) {
		coEvery { libraryApi.getItem(itemId = item.id) } returns mockk<Response<BaseItemDto>> { every { content } returns item }
		coEvery { itemsApi.getItems(parentId = any(), includeItemTypes = any(), fields = any(), sortBy = any(), sortOrder = any()) } returns
			mockk<Response<BaseItemDtoQueryResult>> { every { content } returns BaseItemDtoQueryResult(items = album, totalRecordCount = album.size, startIndex = 0) }
		model.loadItem(item.id, setOf(ItemSortBy.SORT_NAME), SortOrder.ASCENDING)
	}
}

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPlayerViewModelTests : FunSpec({
	suspend fun fixture(block: suspend TestScope.(PhotoFixture) -> Unit) = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val f = PhotoFixture()
		try { block(f) } finally { f.store.clear(); Dispatchers.resetMain() }
	}
	test("previous is safe when the requested photo is absent from a nonempty album") { fixture { f ->
		val selected = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.PHOTO, parentId = f.parentId)
		f.load(selected)
		f.model.showPrevious()
		f.model.currentItem.value shouldBe f.photos.last()
		f.model.showNext()
		f.model.currentItem.value shouldBe selected
	} }
	test("next and previous wrap around an ordinary album") { fixture { f ->
		f.load()
		f.model.showPrevious()
		f.model.currentItem.value shouldBe f.photos.last()
		f.model.showNext()
		f.model.currentItem.value shouldBe f.photos.first()
	} }
	test("empty albums retain the requested photo") { fixture { f ->
		f.load(album = emptyList())
		f.model.showNext(); f.model.showPrevious()
		f.model.currentItem.value shouldBe f.photos.first()
	} }
	test("photos without a parent cannot browse the entire library") { fixture { f ->
		val item = f.photos.first().copy(parentId = null)
		f.load(item)
		f.model.showNext()
		f.model.currentItem.value shouldBe item
		coVerify(exactly = 0) { f.itemsApi.getItems(parentId = any(), includeItemTypes = any(), fields = any(), sortBy = any(), sortOrder = any()) }
	} }
	test("album request failures retain the selected photo") { fixture { f ->
		f.load()
		coEvery { f.itemsApi.getItems(parentId = any(), includeItemTypes = any(), fields = any(), sortBy = any(), sortOrder = any()) } throws ApiClientException("offline")
		f.model.loadItem(f.photos.first().id, setOf(ItemSortBy.SORT_NAME), SortOrder.ASCENDING)
		f.model.showPrevious()
		f.model.currentItem.value shouldBe f.photos.first()
	} }
	test("slideshow advances on the main scope and manual navigation resets its timer") { fixture { f ->
		f.load()
		f.model.startPresentation()
		advanceTimeBy(8_000); runCurrent()
		f.model.currentItem.value shouldBe f.photos[1]
		advanceTimeBy(4_000)
		f.model.showNext()
		advanceTimeBy(4_000); runCurrent()
		f.model.currentItem.value shouldBe f.photos[2]
		advanceTimeBy(4_000); runCurrent()
		f.model.currentItem.value shouldBe f.photos[0]
	} }
	test("slideshow settings pause presentation and closing them restarts the timer") { fixture { f ->
		f.load()
		f.model.setSettingsVisible(true)
		f.model.startPresentation()
		advanceTimeBy(16_000); runCurrent()
		f.model.currentItem.value shouldBe f.photos[0]
		f.model.setSettingsVisible(false)
		advanceTimeBy(8_000); runCurrent()
		f.model.currentItem.value shouldBe f.photos[1]
		f.model.stopPresentation()
		advanceTimeBy(16_000); runCurrent()
		f.model.currentItem.value shouldBe f.photos[1]
	} }
	test("a zero slideshow interval is clamped to the supported minimum") { fixture { f ->
		f.load()
		every { f.preferences[UserPreferences.photoPlayerPresentationDelay] } returns 0L
		f.model.startPresentation()
		advanceTimeBy(3_999); runCurrent()
		f.model.currentItem.value shouldBe f.photos[0]
		advanceTimeBy(1); runCurrent()
		f.model.currentItem.value shouldBe f.photos[1]
	} }
})
