// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.QueryType

class ShortsFolderFragment : BrowseFolderFragment() {
	private companion object {
		const val PAGE_SIZE = 40
	}

	override suspend fun setupQueries(rowLoader: RowLoader) {
		val library = requireNotNull(folder)
		title = "${library.name} / ${getString(R.string.lbl_shorts)}"
		val labels = listOf(R.string.lbl_shorts_genres, R.string.lbl_shorts_tags)
		AutomaticFolders.shortsRequests(library).forEachIndexed { index, query ->
			rows.add(BrowseRowDef(getString(labels[index]), query, PAGE_SIZE))
		}
		rows.add(
			BrowseRowDef(
				getString(R.string.lbl_shorts_runtime), AutomaticFolders.shortRuntimeRequest(library),
				PAGE_SIZE, false, false, null, QueryType.ShortRuntime,
			),
		)
		rowLoader.loadRows(rows)
	}
}
