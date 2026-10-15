// Modified for the Parmesh fork on 2026-10-13. See FORK.md.
package org.jellyfin.androidtv.data.eventhandling

import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateRequest
import org.jellyfin.sdk.model.extensions.ticks

/** Explicit pause commands are idempotent; only PLAY_PAUSE toggles playback. */
internal fun PlaybackController.handleRemotePlayState(request: PlaystateRequest?) {
	when (request?.command) {
		PlaystateCommand.STOP -> endPlayback(true)
		PlaystateCommand.PAUSE -> if (isPlaying) pause()
		PlaystateCommand.UNPAUSE -> if (isPaused) playPause()
		PlaystateCommand.PLAY_PAUSE -> playPause()
		PlaystateCommand.NEXT_TRACK -> next()
		PlaystateCommand.PREVIOUS_TRACK -> prev()
		PlaystateCommand.SEEK -> request.seekPositionTicks?.let { seek(it.ticks.inWholeMilliseconds.coerceAtLeast(0)) }
		PlaystateCommand.REWIND -> rewind()
		PlaystateCommand.FAST_FORWARD -> fastForward()
		null -> Unit
	}
}
