package org.jellyfin.androidtv.util.sdk

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

private class SdkPlaybackFixture {
	val context = mockk<FragmentActivity>(relaxed = true)
	val registry = LifecycleRegistry.createUnsafe(context)
	val itemsApi = mockk<ItemsApi>()
	val api = mockk<ApiClient> { every { getOrCreateApi(ItemsApi::class, any()) } returns itemsApi }
	val preferences = mockk<UserPreferences> { every { get(UserPreferences.resumeSubtractDuration) } returns "0" }
	val launcher = mockk<PlaybackLauncher>(relaxed = true)
	val helper = SdkPlaybackHelper(api, preferences, launcher, PlaybackControllerContainer())
	val toast = mockk<Toast>(relaxed = true)
	init {
		every { context.lifecycle } returns registry
		registry.currentState = Lifecycle.State.STARTED
		every { Toast.makeText(context, R.string.msg_no_playable_items, Toast.LENGTH_LONG) } returns toast
	}
	fun respond(items: List<BaseItemDto>) {
		coEvery { itemsApi.getItems(ids = any()) } returns Response(
			BaseItemDtoQueryResult(items = items, totalRecordCount = items.size, startIndex = 0), 200, emptyMap()
		)
	}
	suspend fun finish() { context.lifecycleScope.coroutineContext[Job]!!.children.toList().joinAll() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SdkPlaybackHelperTests : FunSpec({
	suspend fun fixture(block: suspend (SdkPlaybackFixture) -> Unit) = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		mockkStatic(Toast::class)
		val f = SdkPlaybackFixture()
		try { block(f) } finally {
			f.registry.currentState = Lifecycle.State.DESTROYED
			unmockkStatic(Toast::class)
			Dispatchers.resetMain()
		}
	}
	test("an empty remote item request cannot query or replace the playing queue") { fixture { f ->
		f.respond(emptyList())
		f.helper.retrieveAndPlay(emptyList(), false, null, null, f.context)
		f.finish()
		coVerify(exactly = 0) { f.itemsApi.getItems(ids = any()) }
		verify(exactly = 0) { f.launcher.launch(any(), any(), any(), any(), any(), any()) }
	} }
	test("missing requested titles report no playable items without crashing or replacing the queue") { fixture { f ->
		f.respond(emptyList())
		f.helper.retrieveAndPlay(listOf(UUID.randomUUID()), false, null, null, f.context)
		f.finish()
		verify(exactly = 1) { f.toast.show() }
		verify(exactly = 0) { f.launcher.launch(any(), any(), any(), any(), any(), any()) }
	} }
	test("missing titles with an explicit position cannot clear an existing playback queue") { fixture { f ->
		f.respond(emptyList())
		f.helper.retrieveAndPlay(listOf(UUID.randomUUID()), false, 0, null, f.context)
		f.finish()
		verify(exactly = 0) { f.launcher.launch(any(), any(), any(), any(), any(), any()) }
	} }
	test("a valid remote item request preserves its explicit seek position") { fixture { f ->
		val item = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE)
		f.respond(listOf(item))
		f.helper.retrieveAndPlay(listOf(item.id), false, 120_000_000, null, f.context)
		f.finish()
		verify(exactly = 1) { f.launcher.launch(f.context, listOf(item), 12_000, false, 0, false) }
	} }
})
