package org.jellyfin.androidtv.ui.playback

import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.TrackGroup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

class SubtitleTrackTests : FunSpec({
	beforeSpec {
		mockkStatic(TextUtils::class)
		every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
	}
	afterSpec { unmockkStatic(TextUtils::class) }

	fun group(id: String, type: Int = C.TRACK_TYPE_TEXT): Tracks.Group {
		val format = Format.Builder().setId(id).setSampleMimeType(if (type == C.TRACK_TYPE_TEXT) "application/x-subrip" else "video/avc").build()
		return Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
	}

	test("all merged external subtitles are excluded when selecting embedded tracks") {
		val first = group("embedded-en")
		val second = group("embedded-es")
		val groups = listOf(group("0", C.TRACK_TYPE_VIDEO), group("1:JF_EXTERNAL:17"), first, group("2:JF_EXTERNAL:38"), second)
		groups.embeddedSubtitleGroup(0) shouldBe first
		groups.embeddedSubtitleGroup(1) shouldBe second
	}
	test("a missing embedded track does not select an external subtitle") {
		listOf(group("1:JF_EXTERNAL:17")).embeddedSubtitleGroup(0) shouldBe null
		emptyList<Tracks.Group>().embeddedSubtitleGroup(0) shouldBe null
	}
})
