package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PlaybackTeardownTests : FunSpec({
	test("a late ready callback after player teardown is ignored") {
		// Constructor-free mock reproduces the cleared fragment and stream fields after teardown.
		val controller = mockk<PlaybackController>(relaxed = true)
		every { controller.onPrepared() } answers { callOriginal() }
		controller.onPrepared()
		verify(exactly = 0) { controller.switchAudioStream(any()) }
	}
	test("a late completion callback cannot advance a closed queue") {
		val controller = mockk<PlaybackController>(relaxed = true)
		every { controller.onCompletion() } answers { callOriginal() }
		controller.onCompletion()
		verify(exactly = 0) { controller.endPlayback(any()) }
	}
})
