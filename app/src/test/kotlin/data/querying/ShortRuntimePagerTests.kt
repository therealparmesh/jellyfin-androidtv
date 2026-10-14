// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
package org.jellyfin.androidtv.data.querying

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.supervisorScope
import org.jellyfin.androidtv.ui.browsing.AutomaticFolders
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.io.IOException
import java.util.UUID

class ShortRuntimePagerTests : FunSpec({
	val query = AutomaticFolders.shortRuntimeRequest(
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.MOVIES),
	)
	val cutoff = 18_000_000_000L
	fun movie(ticks: Long?) = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, runTimeTicks = ticks)
	fun response(items: List<BaseItemDto>, total: Int = items.size) =
		BaseItemDtoQueryResult(items = items, totalRecordCount = total, startIndex = 0)

	test("positive runtimes below 30 minutes qualify but exactly 30 minutes and longer do not") {
		val items = listOf(movie(1), movie(cutoff - 1), movie(cutoff), movie(cutoff + 1))
		val pager = ShortRuntimePager(query)
		val page = pager.load(40) { response(items, 1000) }
		page.items shouldBe items.take(2)
		page.hasMore shouldBe false
		pager.load(40) { error("Must stop before fetching the longer movies") }.items shouldBe emptyList()
	}

	test("unknown and nonpositive runtime pages advance server offsets without skipping or duplicating matches") {
		val items = listOf(movie(null), movie(-1), movie(0), movie(1), movie(2), movie(cutoff - 1), movie(cutoff))
		val offsets = mutableListOf<Int>()
		val fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult = {
			val offset = requireNotNull(it.startIndex)
			offsets.add(offset)
			it.limit shouldBe 2
			it.parentId shouldBe query.parentId
			response(items.drop(offset).take(2), items.size)
		}
		val pager = ShortRuntimePager(query)
		pager.load(2, fetch) shouldBe ShortRuntimePager.Page(listOf(items[3]), true)
		pager.load(2, fetch) shouldBe ShortRuntimePager.Page(items.subList(4, 6), true)
		pager.load(2, fetch) shouldBe ShortRuntimePager.Page(emptyList(), false)
		offsets shouldBe listOf(0, 2, 4, 6)
	}

	test("a library with only unknown runtimes or long movies has no runtime matches") {
		val pager = ShortRuntimePager(query)
		pager.load(40) { response(listOf(movie(null), movie(0), movie(cutoff))) } shouldBe ShortRuntimePager.Page(emptyList(), false)
	}

	test("a final full page of short movies completes without an extra request") {
		val items = listOf(movie(1), movie(2))
		val pager = ShortRuntimePager(query)
		pager.load(2) { response(items) } shouldBe ShortRuntimePager.Page(items, false)
		pager.load(2) { error("No next page exists") }.hasMore shouldBe false
	}

	test("an unexpectedly empty server page ends paging even when its total is stale") {
		val pager = ShortRuntimePager(query)
		pager.load(2) { response(emptyList(), 100) } shouldBe ShortRuntimePager.Page(emptyList(), false)
		pager.load(2) { error("Must not loop on an empty page") }.hasMore shouldBe false
	}

	test("a failed next-page request can retry the same offset") {
		val pager = ShortRuntimePager(query)
		pager.load(1) { response(listOf(movie(1)), 5) }.hasMore shouldBe true
		shouldThrow<IOException> { pager.load(1) { throw IOException("offline") } }
		val item = movie(2)
		pager.load(1) {
			it.startIndex shouldBe 1
			response(listOf(item), 2)
		} shouldBe ShortRuntimePager.Page(listOf(item), false)
	}

	test("cancellation after receiving a page does not consume its offset") {
		val pager = ShortRuntimePager(query)
		supervisorScope {
			val attempt = async {
				pager.load(1) {
					currentCoroutineContext().cancel()
					response(listOf(movie(1)), 5)
				}
			}
			shouldThrow<CancellationException> { attempt.await() }
		}
		pager.load(1) {
			it.startIndex shouldBe 0
			response(listOf(movie(1)))
		}.hasMore shouldBe false
	}

	test("invalid page sizes and unsorted requests are rejected before fetching") {
		val pager = ShortRuntimePager(query)
		for (size in listOf(0, -1)) {
			shouldThrow<IllegalArgumentException> { pager.load(size) { error("Invalid request") } }
		}
		shouldThrow<IllegalArgumentException> { ShortRuntimePager(query.copy(sortBy = null)) }
		shouldThrow<IllegalArgumentException> { ShortRuntimePager(query.copy(sortOrder = null)) }
	}
})
