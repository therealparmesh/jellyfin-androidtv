package org.jellyfin.androidtv.util.profile

import android.util.Size
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.constant.Codec
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

class DeviceProfileTests : FunSpec({
	fun profile(dts: Boolean = true, trueHd: Boolean = true, ass: Boolean = true, downmix: Boolean = false) = createDeviceProfile(
		mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true) {
			every { getMaxResolution(any()) } returns mockk<Size> {
				every { width } returns 3840
				every { height } returns 2160
			}
		},
		maxBitrate = Int.MAX_VALUE,
		isAC3PrefEnabled = true,
		isEAC3PrefEnabled = true,
		isDTSPrefEnabled = dts,
		isTrueHDPrefEnabled = trueHd,
		downMixAudio = downmix,
		assDirectPlay = ass,
		pgsDirectPlay = true,
		userAVCLevel = null,
		userHEVCLevel = null,
		forceEnabledHdr = emptySet(),
		forceDisabledHdr = emptySet(),
	)

	test("explicit bitrate limits are retained in device profiles") {
		parseMaxBitrate("100") shouldBe 100_000_000
		parseMaxBitrate("0.72") shouldBe 720_000
		val profile = profile()
		profile.maxStaticBitrate shouldBe Int.MAX_VALUE
		profile.maxStreamingBitrate shouldBe Int.MAX_VALUE
	}

	test("legacy and invalid bitrate values cannot crash or overflow") {
		for (value in listOf("0", "-1", "NaN", "Infinity", "", "broken", "original")) parseMaxBitrate(value) shouldBe 100_000_000
		parseMaxBitrate("999999999999") shouldBe Int.MAX_VALUE
	}

	test("unsupported DTS and TrueHD also exclude the ffprobe aliases") {
		val codecNames = profile(dts = false, trueHd = false).directPlayProfiles
			.first { it.type == DlnaProfileType.VIDEO }.audioCodec.orEmpty().split(',')
		for (codec in listOf(Codec.Audio.DTS, Codec.Audio.DCA, Codec.Audio.TRUEHD, Codec.Audio.MLP)) {
			codecNames.contains(codec) shouldBe false
		}
	}

	test("supported audio keeps local playback eligible for DTS and TrueHD aliases") {
		val codecNames = profile().directPlayProfiles.first { it.type == DlnaProfileType.VIDEO }.audioCodec.orEmpty().split(',')
		for (codec in listOf(Codec.Audio.DTS, Codec.Audio.DCA, Codec.Audio.TRUEHD, Codec.Audio.MLP)) {
			codecNames.contains(codec) shouldBe true
		}
	}

	test("ASS can be delivered locally and burn-in remains a fallback") {
		val methods = profile().subtitleProfiles.filter { it.format == Codec.Subtitle.ASS }.map { it.method }.toSet()
		methods shouldBe setOf(SubtitleDeliveryMethod.EMBED, SubtitleDeliveryMethod.EXTERNAL, SubtitleDeliveryMethod.ENCODE)
		profile(ass = false).subtitleProfiles.filter { it.format == Codec.Subtitle.ASS }.map { it.method } shouldBe listOf(SubtitleDeliveryMethod.ENCODE)
	}

	test("explicit stereo downmix keeps the restricted audio profile") {
		val codecs = profile(downmix = true).directPlayProfiles.first { it.type == DlnaProfileType.VIDEO }.audioCodec.orEmpty().split(',').toSet()
		codecs shouldBe setOf(Codec.Audio.AAC, Codec.Audio.MP2, Codec.Audio.MP3)
	}
})
