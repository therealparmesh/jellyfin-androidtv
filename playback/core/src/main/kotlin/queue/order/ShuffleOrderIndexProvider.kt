// Modified for the Parmesh fork on 2026-10-13. See FORK.md.
package org.jellyfin.playback.core.queue.order

internal class ShuffleOrderIndexProvider : OrderIndexProvider {
	private val nextIndices = mutableListOf<Int>()

	override fun reset() = nextIndices.clear()

	override fun provideIndices(
		amount: Int,
		size: Int,
		playedIndices: Collection<Int>,
		currentIndex: Int,
	): Collection<Int> {
		if (amount <= 0 || size <= 0) return emptyList()
		val excluded = playedIndices.toMutableSet().apply { add(currentIndex) }
		nextIndices.removeAll { it !in 0 until size || it in excluded }
		if (nextIndices.size < amount) {
			val cached = nextIndices.toSet()
			val available = (0 until size).filterNot { it in excluded || it in cached }.shuffled()
			nextIndices.addAll(available.take(amount - nextIndices.size))
		}
		return nextIndices.take(amount)
	}

	override fun notifyRemoved(index: Int) {
		nextIndices.removeAll { it == index }
		nextIndices.replaceAll { if (it > index) it - 1 else it }
	}

	override fun useNextIndex() {
		if (nextIndices.isNotEmpty()) nextIndices.removeAt(0)
	}
}
