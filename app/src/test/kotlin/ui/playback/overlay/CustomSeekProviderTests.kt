// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.playback.overlay

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class CustomSeekProviderTests : FunSpec({
	test("CustomSeekProvider.seekPositions with simple duration from beginning") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 30000L
			every { currentPosition } returns 0L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(0L, 10000L, 20000L, 30000L)
	}

	test("CustomSeekProvider.seekPositions with simple duration from end") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 30000L
			every { currentPosition } returns 30000L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(0L, 10000L, 20000L, 30000L)
	}

	test("CustomSeekProvider.seekPositions with odd duration from beginning") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 45000L
			every { currentPosition } returns 0L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(0L, 10000, 20000, 30000, 40000, 45000)
	}

	test("CustomSeekProvider.seekPositions with seek disabled") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns false
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions.size shouldBe 0
	}

	test("CustomSeekProvider.seekPositions from a mid-video position") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 32_500L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(
			0, 2500, 12500, 22500, 32500, 42500, 52500, 60000
		)
	}

	test("CustomSeekProvider.seekPositions from a near-beginning position") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 10_500L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(
			0, 500, 10500, 20500, 30500, 40500, 50500, 60000
		)
	}

	test("CustomSeekProvider.seekPositions from a within first second position") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 500L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(
			0, 500, 10500, 20500, 30500, 40500, 50500, 60000
		)
	}

	test("CustomSeekProvider.seekPositions from a near-end position") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 49_500L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(
			0, 9500, 19500, 29500, 39500, 49500, 59500, 60000
		)
	}

	test("CustomSeekProvider.seekPositions from within a last second position") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 59_500L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(
			0, 9500, 19500, 29500, 39500, 49500, 59500, 60000
		)
	}

	test("CustomSeekProvider.seekPositions with zero duration") {
		val videoPlayerAdapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 0L
			every { currentPosition } returns 0L
		}
		val customSeekProvider = CustomSeekProvider(videoPlayerAdapter, mockk(), mockk(), mockk(), 10_000)

		customSeekProvider.seekPositions shouldBe longArrayOf(0)
	}
	test("short videos have unique seek points including both boundaries") {
		val adapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 5_000L
			every { currentPosition } returns 2_000L
		}
		CustomSeekProvider(adapter, mockk(), mockk(), mockk(), 10_000).seekPositions shouldBe longArrayOf(0, 2_000, 5_000)
	}

	test("unknown duration cannot yield negative seek points") {
		val adapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns -1L
			every { currentPosition } returns 5_000L
		}
		CustomSeekProvider(adapter, mockk(), mockk(), mockk(), 10_000).seekPositions shouldBe longArrayOf(0)
	}

	test("seek positions remain increasing and in range across boundaries and invalid intervals") {
		for (duration in listOf(0L, 1L, 4_999L, 10_000L, 10_001L, 60_000L)) {
			for (current in listOf(-1L, 0L, duration / 2, duration, duration + 1_000)) {
				for (step in listOf(-1L, 0L, 1_000L, 10_000L, 60_000L)) {
					val adapter = mockk<VideoPlayerAdapter> {
						every { canSeek() } returns true
						every { this@mockk.duration } returns duration
						every { currentPosition } returns current
					}
					val points = CustomSeekProvider(adapter, mockk(), mockk(), mockk(), step).seekPositions
					points.first() shouldBe 0L
					points.last() shouldBe duration
					points.toList().zipWithNext().all { (a, b) -> a < b } shouldBe true
					points.all { it in 0L..duration } shouldBe true
					points.contains(current.coerceIn(0, duration)) shouldBe true
				}
			}
		}
	}

	test("extreme metadata cannot overflow or allocate an unbounded seek array") {
		val adapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns Long.MAX_VALUE
			every { currentPosition } returns Long.MAX_VALUE / 2
		}
		val points = CustomSeekProvider(adapter, mockk(), mockk(), mockk(), 1).seekPositions
		(points.size <= 10_002) shouldBe true
		points.last() shouldBe Long.MAX_VALUE
		points.toList().zipWithNext().all { (a, b) -> a < b } shouldBe true
	}

	test("thumbnail requests reject negative indices before accessing media") {
		val adapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 0L
		}
		val provider = CustomSeekProvider(adapter, mockk(), mockk(), mockk(), 10_000)
		provider.seekPositions
		provider.getThumbnail(-1, mockk())
	}

	test("losing seek support clears thumbnail positions from the preceding item") {
		val adapter = mockk<VideoPlayerAdapter> {
			every { canSeek() } returns true
			every { duration } returns 60_000L
			every { currentPosition } returns 0L
		}
		val provider = CustomSeekProvider(adapter, mockk(), mockk(), mockk(), 10_000)
		provider.seekPositions
		every { adapter.canSeek() } returns false
		provider.seekPositions shouldBe longArrayOf()
		provider.getThumbnail(0, mockk())
	}

})

