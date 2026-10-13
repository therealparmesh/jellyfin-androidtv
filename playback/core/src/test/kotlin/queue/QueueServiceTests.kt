package org.jellyfin.playback.core.queue

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jellyfin.playback.core.PlayerState
import org.jellyfin.playback.core.model.RepeatMode
import org.jellyfin.playback.core.queue.supplier.QueueSupplier

class QueueServiceTests : FunSpec({
	fun fixture(): Pair<QueueService, Job> {
		val queue = spyk(QueueService())
		coEvery { queue.onInitialize() } returns Unit
		val state = mockk<PlayerState>(relaxed = true)
		every { state.repeatMode } returns MutableStateFlow(RepeatMode.NONE)
		val job = Job()
		queue.initialize(mockk(relaxed = true), state, job)
		return queue to job
	}
	suspend fun QueueService.load(items: List<QueueEntry>) {
		addSupplier(object : QueueSupplier {
			override val size = items.size
			override suspend fun getItem(index: Int) = items.getOrNull(index)
		})
		withTimeout(5_000) { entry.first { it == items.first() } }
	}
	test("clearing removed entries restores the size of a replacement queue") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val first = List(3) { QueueEntry() }
				queue.load(first)
				queue.peekNext(2)
				queue.removeEntry(first[2])
				queue.estimatedSize shouldBe 2
				queue.clear()
				queue.load(List(3) { QueueEntry() })
				queue.estimatedSize shouldBe 3
				queue.peekNext(10).size shouldBe 2
			} finally { job.cancel() }
		}
	}
	test("removing the final active track clears the active entry") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val item = QueueEntry()
				queue.load(listOf(item))
				queue.removeEntry(item)
				queue.entry.value shouldBe null
				queue.entryIndex.value shouldBe Queue.INDEX_NONE
				queue.estimatedSize shouldBe 0
			} finally { job.cancel() }
		}
	}
	test("removing the active middle track cannot duplicate Previous history") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				val items = List(3) { QueueEntry() }
				queue.load(items)
				queue.next() shouldBe items[1]
				queue.removeEntry(items[1])
				queue.entry.value shouldBe items[2]
				queue.previous() shouldBe items[0]
				queue.previous() shouldBe null
			} finally { job.cancel() }
		}
	}
	test("nonpositive queue peek requests return nothing in every repeat mode") {
		runBlocking {
			val (queue, job) = fixture()
			try {
				for (mode in RepeatMode.entries) {
					every { queue.state.repeatMode } returns MutableStateFlow(mode)
					queue.peekNext(0, useRepeatMode = true).size shouldBe 0
					queue.peekNext(-1, useRepeatMode = true).size shouldBe 0
				}
			} finally { job.cancel() }
		}
	}
})
