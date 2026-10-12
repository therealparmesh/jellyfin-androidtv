// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

class ByGenreFragment : BrowseFolderFragment() {
	private val apiClient by inject<ApiClient>()

	override suspend fun setupQueries(rowLoader: RowLoader) {
		// Get all genres for this folder
		val genresResponse = withContext(Dispatchers.IO) {
			apiClient.genresApi.getGenres(
				parentId = folder?.id,
				includeItemTypes = includeType?.let(BaseItemKind::fromNameOrNull)?.let(::setOf),
				sortBy = setOf(ItemSortBy.SORT_NAME),
			).content
		}

		for (genre in genresResponse.items) {
			val itemsRequest = GetItemsRequest(
				parentId = folder?.id,
				sortBy = setOf(ItemSortBy.SORT_NAME),
				includeItemTypes = includeType?.let(BaseItemKind::fromNameOrNull)?.let(::setOf),
				genres = setOf(genre.name.orEmpty()),
				recursive = true,
				fields = ItemRepository.browseFields,
			)
			rows.add(BrowseRowDef(genre.name, itemsRequest, 40))
		}

		rowLoader.loadRows(rows)
	}
}
