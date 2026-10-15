package org.jellyfin.androidtv.data.eventhandling

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateRequest

class RemotePlayStateTests : FunSpec({
	test("pause pauses a playing video and never toggles an already paused one") {
		val player = mockk<PlaybackController>(relaxed = true)
		every { player.isPlaying } returns true
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.PAUSE))
		every { player.isPlaying } returns false
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.PAUSE))
		verify(exactly = 1) { player.pause() }
		verify(exactly = 0) { player.playPause() }
	}
	test("unpause resumes a paused video and never pauses an already playing one") {
		val player = mockk<PlaybackController>(relaxed = true)
		every { player.isPaused } returns true
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.UNPAUSE))
		every { player.isPaused } returns false
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.UNPAUSE))
		verify(exactly = 1) { player.playPause() }
		verify(exactly = 0) { player.pause() }
	}
	test("only the toggle command always toggles playback") {
		val player = mockk<PlaybackController>(relaxed = true)
		repeat(2) { player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.PLAY_PAUSE)) }
		verify(exactly = 2) { player.playPause() }
	}
	test("seek converts ticks and ignores a command without a position") {
		val player = mockk<PlaybackController>(relaxed = true)
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.SEEK))
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.SEEK, seekPositionTicks = 125_000_000))
		player.handleRemotePlayState(PlaystateRequest(PlaystateCommand.SEEK, seekPositionTicks = -1_000_000))
		verify(exactly = 1) { player.seek(12_500) }
		verify(exactly = 1) { player.seek(0) }
		verify(exactly = 2) { player.seek(any()) }
	}
})
