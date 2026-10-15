package org.jellyfin.playback.core.queue.order

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrderIndexProviderTests : FunSpec({
	test("shuffle excludes the current item and never generates the size as an index") {
		repeat(30) {
			val provider = ShuffleOrderIndexProvider()
			val result = provider.provideIndices(20, 5, emptyList(), 2)
			result.toSet() shouldBe setOf(0, 1, 3, 4)
			result.size shouldBe 4
		}
	}
	test("shuffle peeks are stable and consumption advances without duplicates") {
		val provider = ShuffleOrderIndexProvider()
		val first = provider.provideIndices(4, 5, emptyList(), 0).toList()
		provider.provideIndices(4, 5, emptyList(), 0).toList() shouldBe first
		provider.useNextIndex()
		provider.provideIndices(4, 5, listOf(0), first[0]).toList() shouldBe first.drop(1)
	}
	test("shuffle counts distinct valid history and excludes the active track") {
		val provider = ShuffleOrderIndexProvider()
		provider.provideIndices(10, 4, listOf(-1, 0, 0, 9), 1).toSet() shouldBe setOf(2, 3)
	}
	test("shuffle is exhausted when all valid tracks are already used") {
		val provider = ShuffleOrderIndexProvider()
		provider.provideIndices(1, 3, listOf(0, 1), 2).toList() shouldBe emptyList()
	}
	test("empty and nonpositive requests are safe for every playback order") {
		for (provider in listOf(DefaultOrderIndexProvider(), RandomOrderIndexProvider(), ShuffleOrderIndexProvider())) {
			provider.provideIndices(1, 0, emptyList(), -1).toList() shouldBe emptyList()
			provider.provideIndices(-1, 3, emptyList(), 0).toList() shouldBe emptyList()
			provider.provideIndices(0, 3, emptyList(), 0).toList() shouldBe emptyList()
			provider.useNextIndex()
		}
	}
	test("random cached indices stay in bounds if the queue becomes smaller") {
		val provider = RandomOrderIndexProvider()
		provider.provideIndices(100, 20, emptyList(), 0)
		provider.provideIndices(100, 1, emptyList(), 0).toSet() shouldBe setOf(0)
	}
	test("shuffle cached indices are adjusted after removal") {
		val provider = ShuffleOrderIndexProvider()
		provider.provideIndices(10, 5, emptyList(), 0)
		provider.notifyRemoved(2)
		val result = provider.provideIndices(10, 4, emptyList(), 0)
		result.toSet() shouldBe setOf(1, 2, 3)
		result.size shouldBe 3
	}
	test("reset discards cached indices before a replacement queue") {
		for (provider in listOf(RandomOrderIndexProvider(), ShuffleOrderIndexProvider())) {
			provider.provideIndices(20, 20, emptyList(), 0)
			provider.reset()
			provider.provideIndices(1, 1, emptyList(), -1).toList() shouldBe listOf(0)
		}
	}
})
