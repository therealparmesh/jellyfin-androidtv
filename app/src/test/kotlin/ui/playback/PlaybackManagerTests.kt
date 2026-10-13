package org.jellyfin.androidtv.ui.playback

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.androidtv.data.compat.PlaybackException
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response as ApiResponse
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.HlsSegmentApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackErrorCode
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import java.util.UUID

private class PlaybackFixture {
	val owner = mockk<LifecycleOwner>()
	val registry = LifecycleRegistry.createUnsafe(owner)
	val mediaApi = mockk<MediaInfoApi>()
	val encodingApi = mockk<HlsSegmentApi>()
	val api = mockk<ApiClient>(relaxed = true) {
		every { getOrCreateApi(MediaInfoApi::class, any()) } returns mediaApi
		every { getOrCreateApi(HlsSegmentApi::class, any()) } returns encodingApi
	}
	val manager = PlaybackManager(api)
	val options = VideoOptions().apply { itemId = UUID.randomUUID() }
	val successes = mutableListOf<StreamInfo>()
	val errors = mutableListOf<Exception>()
	val callback = object : Response<StreamInfo>() {
		override fun onResponse(response: StreamInfo) { successes.add(response) }
		override fun onError(exception: Exception) { errors.add(exception) }
	}
	init {
		every { owner.lifecycle } returns registry
		registry.currentState = Lifecycle.State.STARTED
	}
	fun respond(response: PlaybackInfoResponse) {
		coEvery { mediaApi.getPostedPlaybackInfo(itemId = any(), data = any()) } returns mockk<ApiResponse<PlaybackInfoResponse>> {
			every { content } returns response
		}
	}
	fun remoteSource(path: String = "https://example.invalid/unit-test.mkv") = mockk<MediaSourceInfo>(relaxed = true) {
		every { supportsDirectPlay } returns true
		every { isRemote } returns true
		every { this@mockk.path } returns path
	}
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerTests : FunSpec({
	suspend fun fixture(block: suspend (PlaybackFixture) -> Unit) = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = PlaybackFixture()
		try { block(fixture) } finally {
			fixture.registry.currentState = Lifecycle.State.DESTROYED
			Dispatchers.resetMain()
		}
	}
	test("valid remote direct-play media remains playable") { fixture { f ->
		f.respond(PlaybackInfoResponse(listOf(f.remoteSource())))
		f.manager.getVideoStreamInfo(f.owner, f.options, 0, f.callback).join()
		f.errors.size shouldBe 0
		f.successes.single().mediaUrl shouldBe "https://example.invalid/unit-test.mkv"
		f.successes.single().playMethod shouldBe PlayMethod.DIRECT_PLAY
	} }
	test("canceling a playback-info request never invokes an error or success callback") { fixture { f ->
		val started = CompletableDeferred<Unit>()
		coEvery { f.mediaApi.getPostedPlaybackInfo(itemId = any(), data = any()) } coAnswers {
			started.complete(Unit)
			awaitCancellation()
		}
		val job = f.manager.getVideoStreamInfo(f.owner, f.options, 0, f.callback)
		started.await()
		job.cancelAndJoin()
		f.errors.size shouldBe 0
		f.successes.size shouldBe 0
	} }
	test("server playback errors retain their type and specific reason") { fixture { f ->
		f.respond(PlaybackInfoResponse(emptyList(), errorCode = PlaybackErrorCode.NOT_ALLOWED))
		f.manager.getVideoStreamInfo(f.owner, f.options, 0, f.callback).join()
		(f.errors.single() as PlaybackException).errorCode shouldBe PlaybackErrorCode.NOT_ALLOWED
		f.successes.size shouldBe 0
	} }
	test("an empty media-source response cannot start an uninitialized player") { fixture { f ->
		f.respond(PlaybackInfoResponse(emptyList()))
		f.manager.getVideoStreamInfo(f.owner, f.options, 0, f.callback).join()
		(f.errors.single() as PlaybackException).errorCode shouldBe PlaybackErrorCode.NO_COMPATIBLE_STREAM
		f.successes.size shouldBe 0
	} }
	test("a source with no supported delivery mode is rejected") { fixture { f ->
		f.respond(PlaybackInfoResponse(listOf(mockk<MediaSourceInfo>(relaxed = true))))
		f.manager.getVideoStreamInfo(f.owner, f.options, 0, f.callback).join()
		(f.errors.single() as PlaybackException).errorCode shouldBe PlaybackErrorCode.NO_COMPATIBLE_STREAM
		f.successes.size shouldBe 0
	} }
	test("blank remote URLs cannot start playback") { fixture { f ->
		f.respond(PlaybackInfoResponse(listOf(f.remoteSource(""))))
		f.manager.getVideoStreamInfo(f.owner, f.options, 0, f.callback).join()
		(f.errors.single() as PlaybackException).errorCode shouldBe PlaybackErrorCode.NO_COMPATIBLE_STREAM
		f.successes.size shouldBe 0
	} }
	test("failed cleanup of an old encoder does not prevent negotiating the next stream") { fixture { f ->
		coEvery { f.encodingApi.stopEncodingProcess(any(), any()) } throws ApiClientException("Encoder already stopped")
		f.respond(PlaybackInfoResponse(listOf(f.remoteSource())))
		val old = StreamInfo().apply { playSessionId = "old"; playMethod = PlayMethod.TRANSCODE }
		f.manager.changeVideoStream(f.owner, old, f.options, 0, f.callback).join()
		f.errors.size shouldBe 0
		f.successes.size shouldBe 1
	} }
	test("canceling encoder cleanup does not negotiate another stream") { fixture { f ->
		val started = CompletableDeferred<Unit>()
		coEvery { f.encodingApi.stopEncodingProcess(any(), any()) } coAnswers {
			started.complete(Unit)
			awaitCancellation()
		}
		val old = StreamInfo().apply { playSessionId = "old"; playMethod = PlayMethod.TRANSCODE }
		val job = f.manager.changeVideoStream(f.owner, old, f.options, 0, f.callback)
		started.await()
		job.cancelAndJoin()
		coVerify(exactly = 0) { f.mediaApi.getPostedPlaybackInfo(itemId = any(), data = any()) }
		f.errors.size shouldBe 0
		f.successes.size shouldBe 0
	} }
})
