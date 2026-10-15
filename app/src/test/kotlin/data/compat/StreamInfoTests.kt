package org.jellyfin.androidtv.data.compat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

class StreamInfoTests : FunSpec({
	fun stream(index: Int, type: MediaStreamType, delivery: SubtitleDeliveryMethod? = null) = mockk<MediaStream> {
		every { this@mockk.index } returns index
		every { this@mockk.type } returns type
		every { deliveryMethod } returns delivery
	}
	fun source(streams: List<MediaStream>?, subtitle: Int? = null) = mockk<MediaSourceInfo> {
		every { mediaStreams } returns streams
		every { defaultSubtitleStreamIndex } returns subtitle
	}

	test("subtitle delivery uses the stream ID rather than its list position") {
		val source = source(listOf(stream(0, MediaStreamType.VIDEO), stream(12, MediaStreamType.SUBTITLE, SubtitleDeliveryMethod.EMBED)), 12)
		StreamInfo().apply { mediaSource = source }.subtitleDeliveryMethod shouldBe SubtitleDeliveryMethod.EMBED
	}
	test("missing or stale subtitle indices safely drop subtitles") {
		for (index in listOf(null, -1, 3, 99)) {
			StreamInfo().apply { mediaSource = source(listOf(stream(12, MediaStreamType.SUBTITLE, SubtitleDeliveryMethod.EMBED)), index) }
				.subtitleDeliveryMethod shouldBe SubtitleDeliveryMethod.DROP
		}
	}
	test("a non-subtitle stream or unspecified delivery cannot become a subtitle") {
		for (track in listOf(stream(5, MediaStreamType.AUDIO), stream(5, MediaStreamType.SUBTITLE))) {
			StreamInfo().apply { mediaSource = source(listOf(track), 5) }.subtitleDeliveryMethod shouldBe SubtitleDeliveryMethod.DROP
		}
	}
	test("sparse audio stream IDs remain selectable and wrong track types are rejected") {
		val audio = stream(27, MediaStreamType.AUDIO)
		val source = source(listOf(stream(0, MediaStreamType.VIDEO), audio))
		StreamInfo.findMediaStream(source, 27, MediaStreamType.AUDIO) shouldBe audio
		StreamInfo.findMediaStream(source, 0, MediaStreamType.AUDIO) shouldBe null
		StreamInfo.findMediaStream(source, 1, MediaStreamType.AUDIO) shouldBe null
	}
	test("uninitialized media sources have no selectable streams") {
		StreamInfo().selectableAudioStreams shouldBe emptyList()
		StreamInfo().subtitleDeliveryMethod shouldBe SubtitleDeliveryMethod.DROP
		StreamInfo().apply { mediaSource = source(null) }.selectableAudioStreams shouldBe emptyList()
		StreamInfo.findMediaStream(null, 1, MediaStreamType.AUDIO) shouldBe null
	}
})
