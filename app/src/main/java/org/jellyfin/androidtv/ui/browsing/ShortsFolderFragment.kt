// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.R

class ShortsFolderFragment : BrowseFolderFragment() {
	override suspend fun setupQueries(rowLoader: RowLoader) {
		val library = requireNotNull(folder)
		title = "${library.name} / ${getString(R.string.lbl_shorts)}"
		val labels = listOf(R.string.lbl_shorts_genres, R.string.lbl_shorts_tags)
		AutomaticFolders.shortsRequests(library).forEachIndexed { index, query ->
			rows.add(BrowseRowDef(getString(labels[index]), query, 40))
		}
		rowLoader.loadRows(rows)
	}
}
