// Modified for the Parmesh fork on 2026-10-15. See FORK.md.
package org.jellyfin.androidtv.ui.playback

import android.database.Observable
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.LifecycleCoroutineScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.PlayerState
import org.jellyfin.playback.core.model.PlaybackOrder
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.queue.QueueService
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AudioQueueBaseRowAdapterTests : FunSpec({
	test("an older suspended queue refresh cannot overwrite the newly playing track") {
		runTest {
			fun entry() = QueueEntry().apply { baseItem = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO) }
			val first = entry()
			val second = entry()
			val playing = MutableStateFlow<QueueEntry?>(first)
			val entries = MutableStateFlow(listOf(first))
			val queue = mockk<QueueService> {
				every { this@mockk.entry } returns playing
				every { this@mockk.entries } returns entries
			}
			val state = mockk<PlayerState> { every { playbackOrder } returns MutableStateFlow(PlaybackOrder.DEFAULT) }
			val manager = mockk<PlaybackManager> {
				every { getService(QueueService::class) } returns queue
				every { this@mockk.state } returns state
			}
			val scope = mockk<LifecycleCoroutineScope> { every { coroutineContext } returns backgroundScope.coroutineContext }
			val release = CompletableDeferred<Unit>()
			val started = CompletableDeferred<Unit>()
			var suspendNext = false
			coEvery { queue.peekNext(any(), any(), any()) } coAnswers {
				if (suspendNext) {
					suspendNext = false
					started.complete(Unit)
					release.await()
				}
				emptyList()
			}
			val adapter = AudioQueueBaseRowAdapter(manager, scope)
			val observable = ObjectAdapter::class.java.getDeclaredField("mObservable").apply { isAccessible = true }.get(adapter)
			Observable::class.java.getDeclaredField("mObservers").apply { isAccessible = true }
				.set(observable, arrayListOf<ObjectAdapter.DataObserver>())
			runCurrent()
			suspendNext = true
			entries.value = listOf(first, second)
			runCurrent()
			started.isCompleted shouldBe true
			playing.value = second
			runCurrent()
			release.complete(Unit)
			runCurrent()
			adapter.get(0)?.queueEntry shouldBe second
		}
	}
})
