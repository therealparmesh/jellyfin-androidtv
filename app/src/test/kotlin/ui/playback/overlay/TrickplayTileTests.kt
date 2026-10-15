package org.jellyfin.androidtv.ui.playback.overlay

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.TrickplayInfoDto

class TrickplayTileTests : FunSpec({
	fun info(interval: Int = 10_000, width: Int = 160, height: Int = 90, columns: Int = 4, rows: Int = 2, count: Int = 6) = mockk<TrickplayInfoDto> {
		every { this@mockk.interval } returns interval
		every { this@mockk.width } returns width
		every { this@mockk.height } returns height
		every { tileWidth } returns columns
		every { tileHeight } returns rows
		every { thumbnailCount } returns count
	}
	test("the duration endpoint selects the final real thumbnail") {
		info().tileAt(60_000) shouldBe TrickplayTile(0, 160, 90)
	}
	test("an exact sheet boundary does not request a nonexistent next sheet") {
		info(columns = 5, rows = 5, count = 25).tileAt(250_000) shouldBe TrickplayTile(0, 640, 360)
	}
	test("valid second sheets retain their correct crop offsets") {
		info(interval = 2_000, count = 41).tileAt(18_000) shouldBe TrickplayTile(1, 160, 0)
	}
	test("zero and negative metadata cannot divide by zero or create invalid bitmaps") {
		for (bad in listOf(info(interval = 0), info(interval = -1), info(width = 0), info(height = -1), info(columns = 0), info(rows = 0), info(count = 0))) {
			bad.tileAt(1_000) shouldBe null
		}
		info().tileAt(-1) shouldBe null
	}
	test("tile and pixel geometry cannot overflow") {
		info(columns = 50_000, rows = 50_000).tileAt(1_000) shouldBe null
		info(width = Int.MAX_VALUE, columns = 2).tileAt(1_000) shouldBe null
		info(height = Int.MAX_VALUE, rows = 2).tileAt(1_000) shouldBe null
	}
})
