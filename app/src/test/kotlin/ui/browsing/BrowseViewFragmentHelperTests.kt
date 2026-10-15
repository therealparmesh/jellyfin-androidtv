package org.jellyfin.androidtv.ui.browsing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.LiveTvApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewFragmentHelperTests : FunSpec({
	test("a failed parallel Live TV request reports its error without an uncaught parent failure") {
		runTest {
			Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
			val endpoint = mockk<LiveTvApi>()
			val api = mockk<ApiClient> { every { getOrCreateApi(LiveTvApi::class, any()) } returns endpoint }
			val owner = mockk<LifecycleOwner>()
			val lifecycle = LifecycleRegistry.createUnsafe(owner)
			every { owner.lifecycle } returns lifecycle
			val fragment = mockk<EnhancedBrowseFragment>(relaxed = true) { every { viewLifecycleOwner } returns owner }
			val error = ApiClientException("offline")
			coEvery { endpoint.getRecordings(fields = any(), enableImages = true, limit = 40) } throws error
			coEvery { endpoint.getTimers() } coAnswers { CompletableDeferred<Nothing>().await() }
			startKoin { modules(module { single { api } }) }
			try {
				lifecycle.currentState = Lifecycle.State.STARTED
				val reported = CompletableDeferred<Throwable>()
				fragment.getLiveTvRecordingsAndTimers({ _, _ -> error("Unexpected success") }, { reported.complete(it) })
				withContext(Dispatchers.Default) { withTimeout(5_000) { reported.await() } } shouldBe error
			} finally {
				lifecycle.currentState = Lifecycle.State.DESTROYED
				stopKoin()
				Dispatchers.resetMain()
			}
		}
	}
	for (combined in listOf(false, true)) {
		test("destroyed Live TV views receive no cancellation callbacks (combined=$combined)") {
			runTest {
				Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
				val started = CompletableDeferred<Unit>()
				val finished = CompletableDeferred<Unit>()
				val endpoint = mockk<LiveTvApi>()
				val api = mockk<ApiClient> { every { getOrCreateApi(LiveTvApi::class, any()) } returns endpoint }
				val owner = mockk<LifecycleOwner>()
				val lifecycle = LifecycleRegistry.createUnsafe(owner)
				every { owner.lifecycle } returns lifecycle
				val fragment = mockk<EnhancedBrowseFragment>(relaxed = true) { every { viewLifecycleOwner } returns owner }
				coEvery { endpoint.getRecordings(fields = any(), enableImages = true, limit = 40) } coAnswers {
					CompletableDeferred<Nothing>().await()
				}
				coEvery { endpoint.getTimers() } coAnswers {
					started.complete(Unit)
					try { CompletableDeferred<Nothing>().await() } finally { finished.complete(Unit) }
				}
				startKoin { modules(module { single { api } }) }
				try {
					lifecycle.currentState = Lifecycle.State.STARTED
					var callbacks = 0
					if (combined) fragment.getLiveTvRecordingsAndTimers({ _, _ -> callbacks++ }, { callbacks++ })
					else fragment.getLiveTvTimers({ callbacks++ }, { callbacks++ })
					withContext(Dispatchers.Default) { withTimeout(5_000) { started.await() } }
					lifecycle.currentState = Lifecycle.State.DESTROYED
					withContext(Dispatchers.Default) { withTimeout(5_000) { finished.await() } }
					runCurrent()
					callbacks shouldBe 0
				} finally {
					lifecycle.currentState = Lifecycle.State.DESTROYED
					stopKoin()
					Dispatchers.resetMain()
				}
			}
		}
	}

})
