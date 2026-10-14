// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.GridButtonPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/** Virtual folders use the signed-in user's server queries and never modify library files. */
object AutomaticFolders {
	val familyNames = setOf("Family", "Kids", "Children", "Children's", "Childrens")
	val familyRatings = setOf("G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG")
	val shortNames = setOf("Short", "Shorts", "Short Film", "Short Films")

	fun supportsShorts(library: BaseItemDto) = itemType(library) == BaseItemKind.MOVIE

	fun itemType(library: BaseItemDto): BaseItemKind? = when (library.collectionType) {
		CollectionType.MOVIES -> BaseItemKind.MOVIE
		CollectionType.TVSHOWS -> BaseItemKind.SERIES
		else -> null
	}

	fun genreNames(items: List<BaseItemDto>): List<String> = items.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
		.distinctBy(String::lowercase)
		.sortedWith(String.CASE_INSENSITIVE_ORDER)

	fun genreFolders(library: BaseItemDto, items: List<BaseItemDto>): List<String> = genreNames(items).filterNot { name ->
		familyNames.any { it.equals(name, true) } || (supportsShorts(library) && shortNames.any { it.equals(name, true) })
	}

	private fun itemsRequest(library: BaseItemDto) = GetItemsRequest(
		parentId = library.id,
		includeItemTypes = setOf(requireNotNull(itemType(library))),
		recursive = true,
		sortBy = setOf(ItemSortBy.SORT_NAME),
		fields = ItemRepository.browseFields,
		imageTypeLimit = 1,
	)

	fun familyRequests(library: BaseItemDto): List<GetItemsRequest> {
		val base = itemsRequest(library)
		// The server ANDs different filters. Separate paged rows implement genre OR tag OR rating
		// without downloading the library or excluding titles that match just one criterion.
		return listOf(
			base.copy(genres = familyNames),
			base.copy(tags = familyNames),
			base.copy(officialRatings = familyRatings),
		)
	}

	fun shortsRequests(library: BaseItemDto): List<GetItemsRequest> {
		require(supportsShorts(library)) { "Shorts folders require a movie library" }
		val base = itemsRequest(library)
		return listOf(base.copy(genres = shortNames), base.copy(tags = shortNames))
	}

	fun shortRuntimeRequest(library: BaseItemDto): GetItemsRequest {
		require(supportsShorts(library)) { "Shorts folders require a movie library" }
		return itemsRequest(library).copy(
			sortBy = linkedSetOf(ItemSortBy.RUNTIME, ItemSortBy.SORT_NAME),
			sortOrder = setOf(SortOrder.ASCENDING),
			enableTotalRecordCount = true,
		)
	}
}

class AutomaticFolderButton(text: String, private val action: () -> Unit) : GridButton(-200, text, R.drawable.ic_folder) {
	fun open() = action()
}

fun Fragment.createAutomaticFoldersRow(library: BaseItemDto, owner: LifecycleOwner): ListRow {
	val api by inject<ApiClient>()
	val navigation by inject<NavigationRepository>()
	val buttons = ArrayObjectAdapter(GridButtonPresenter(155, 140, 40))
	val row = ListRow(HeaderItem(getString(R.string.lbl_automatic_folders)), buttons)

	fun load() {
		buttons.clear()
		buttons.add(AutomaticFolderButton(getString(R.string.lbl_family)) {
			navigation.navigate(Destinations.libraryFamily(library))
		})
		if (AutomaticFolders.supportsShorts(library)) {
			buttons.add(AutomaticFolderButton(getString(R.string.lbl_shorts)) {
				navigation.navigate(Destinations.libraryShorts(library))
			})
		}
		owner.lifecycleScope.launch {
			try {
				val genres = withContext(Dispatchers.IO) {
					api.genresApi.getGenres(
						parentId = library.id,
						includeItemTypes = AutomaticFolders.itemType(library)?.let(::setOf),
						sortBy = setOf(ItemSortBy.SORT_NAME),
					).content.items
				}
				for (genre in AutomaticFolders.genreFolders(library, genres)) {
					buttons.add(AutomaticFolderButton(genre) {
						navigation.navigate(Destinations.libraryGenre(library, genre))
					})
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				Timber.w(error, "Failed to retrieve automatic folders")
				buttons.add(AutomaticFolderButton(getString(R.string.lbl_genres_retry)) { load() })
			}
		}
	}
	load()
	return row
}
