// Modified for the Parmesh fork on 2026-10-13. See FORK.md.
package org.jellyfin.playback.core.queue.order

import kotlin.random.Random

internal class RandomOrderIndexProvider : OrderIndexProvider {
	private val nextIndices = mutableListOf<Int>()

	override fun reset() = nextIndices.clear()

	override fun provideIndices(
		amount: Int,
		size: Int,
		playedIndices: Collection<Int>,
		currentIndex: Int,
	): Collection<Int> {
		if (amount <= 0 || size <= 0) return emptyList()
		nextIndices.removeAll { it !in 0 until size }
		return List(amount) { i ->
			if (i <= nextIndices.lastIndex) {
				nextIndices[i]
			} else {
				val index = Random.nextInt(size)
				nextIndices.add(index)
				index
			}
		}
	}

	override fun notifyRemoved(index: Int) {
		nextIndices.removeAll { it == index }
		nextIndices.replaceAll { if (it > index) it - 1 else it }
	}

	override fun useNextIndex() {
		if (nextIndices.isNotEmpty()) nextIndices.removeAt(0)
	}
}
