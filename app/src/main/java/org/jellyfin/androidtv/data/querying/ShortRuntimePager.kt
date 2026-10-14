// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
package org.jellyfin.androidtv.data.querying

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

/** Jellyfin has no runtime filter: page in ascending runtime order and stop at the cutoff. */
class ShortRuntimePager(private val query: GetItemsRequest) {
	companion object {
		const val MAX_RUNTIME_TICKS = 30L * 60 * 10_000_000
	}

	data class Page(val items: List<BaseItemDto>, val hasMore: Boolean)

	private var nextIndex = 0
	private var finished = false

	init {
		require(query.sortBy?.firstOrNull() == ItemSortBy.RUNTIME && query.sortOrder == setOf(SortOrder.ASCENDING))
	}

	suspend fun load(pageSize: Int, fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult): Page {
		require(pageSize > 0)
		while (!finished) {
			val response = fetch(query.copy(startIndex = nextIndex, limit = pageSize))
			currentCoroutineContext().ensureActive()
			// Track server offsets separately: missing/zero runtimes must not shift the next page.
			nextIndex += response.items.size
			finished = response.items.isEmpty() || nextIndex >= response.totalRecordCount ||
				response.items.any { (it.runTimeTicks ?: 0) >= MAX_RUNTIME_TICKS }
			val matches = response.items.filter { (it.runTimeTicks ?: 0) in 1 until MAX_RUNTIME_TICKS }
			if (matches.isNotEmpty() || finished) return Page(matches, !finished)
		}
		return Page(emptyList(), false)
	}
}
