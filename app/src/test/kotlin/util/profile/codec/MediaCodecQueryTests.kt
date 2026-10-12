package org.jellyfin.androidtv.util.profile.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.jellyfin.androidtv.util.AndroidVersion

class MediaCodecQueryTests : FunSpec({
	val mime = "video/hevc"
	val profile = CodecProfileLevel.HEVCProfileMain
	val level = CodecProfileLevel.HEVCMainTierLevel4
	fun decoder(name: String = "vendor.decoder") = mockk<MediaCodecInfo> {
		every { isEncoder } returns false
		every { isSoftwareOnly } returns false
		every { this@mockk.name } returns name
		every { supportedTypes } returns arrayOf(mime)
	}

	beforeEach {
		mockkObject(AndroidVersion)
		every { AndroidVersion.isAtLeastQ } returns true
	}
	afterEach { unmockkObject(AndroidVersion) }

	test("decoder list and native capabilities are queried once per profile") {
		val profileLevel = mockk<CodecProfileLevel>().apply { this.profile = profile; this.level = level }
		val caps = mockk<MediaCodecInfo.CodecCapabilities>().apply { profileLevels = arrayOf(profileLevel) }
		val decoder = decoder()
		every { decoder.getCapabilitiesForType(mime) } returns caps
		val codecs = mockk<MediaCodecList> { every { codecInfos } returns arrayOf(decoder) }
		val query = MediaCodecQuery(codecs, true)
		query.hasDecoder(mime, profile, level) shouldBe true
		query.getDecoderLevel(mime, profile) shouldBe level
		query.hasDecoder(mime.uppercase(), profile, level) shouldBe true
		verify(exactly = 1) { codecs.codecInfos }
		verify(exactly = 1) { decoder.getCapabilitiesForType(mime) }
	}

	test("unsupported types skip native capability calls") {
		val decoder = decoder()
		val codecs = mockk<MediaCodecList> { every { codecInfos } returns arrayOf(decoder) }
		MediaCodecQuery(codecs, true).hasDecoder("video/av1", profile, level) shouldBe false
		verify(exactly = 0) { decoder.getCapabilitiesForType(any()) }
	}

	test("broken vendor capability results are cached instead of retried") {
		val decoder = decoder()
		every { decoder.getCapabilitiesForType(mime) } throws IllegalStateException("vendor failure")
		val codecs = mockk<MediaCodecList> { every { codecInfos } returns arrayOf(decoder) }
		val query = MediaCodecQuery(codecs, true)
		query.hasDecoder(mime, profile, level) shouldBe false
		query.getDecoderLevel(mime, profile) shouldBe 0
		verify(exactly = 1) { decoder.getCapabilitiesForType(mime) }
	}

	test("older Fire OS software decoders respect the disabled software codec setting") {
		every { AndroidVersion.isAtLeastQ } returns false
		val software = decoder("OMX.google.hevc.decoder")
		val codecs = mockk<MediaCodecList> { every { codecInfos } returns arrayOf(software) }
		MediaCodecQuery(codecs, false).hasCodecForMime(mime) shouldBe false
		MediaCodecQuery(codecs, true).hasCodecForMime(mime) shouldBe true
	}
})
