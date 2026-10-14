// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.jellyfin.playback.media3.exoplayer.mapping

import androidx.media3.common.MimeTypes

fun getFfmpegContainerMimeType(codec: String, fallback: String = codec) = codec.lowercase().let { codec ->
	ffmpegContainerMimeTypes[codec]
		?: ffmpegVideoMimeTypes[codec]
		?: ffmpegAudioMimeTypes[codec]
		?: MimeTypes.getMediaMimeType(codec)
		?: fallback
}

val ffmpegContainerMimeTypes = mapOf(
	"aac" to MimeTypes.AUDIO_AAC,
	"alaw" to MimeTypes.AUDIO_ALAW,
	"amr" to MimeTypes.AUDIO_AMR,
	"avi" to MimeTypes.VIDEO_AVI,
	"dts" to MimeTypes.AUDIO_DTS,
	"flac" to MimeTypes.AUDIO_FLAC,
	"flv" to MimeTypes.VIDEO_FLV,
	"matroska" to MimeTypes.APPLICATION_MATROSKA,
	"mkv" to MimeTypes.APPLICATION_MATROSKA,
	"mjpeg" to MimeTypes.VIDEO_MJPEG,
	"mpeg" to MimeTypes.AUDIO_MPEG,
	"ogg" to MimeTypes.AUDIO_OGG,
	"opus" to MimeTypes.AUDIO_OPUS,
	"rtsp" to MimeTypes.APPLICATION_RTSP,
	"truehd" to MimeTypes.AUDIO_TRUEHD,
	"ttml" to MimeTypes.APPLICATION_TTML,
	"vobsub" to MimeTypes.APPLICATION_VOBSUB,
	"wav" to MimeTypes.AUDIO_WAV,
	"webm" to MimeTypes.APPLICATION_WEBM,
// TODO: Find mime types for all these formats...
)
