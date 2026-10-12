package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.R

class FamilyFolderFragment : BrowseFolderFragment() {
	override suspend fun setupQueries(rowLoader: RowLoader) {
		val library = requireNotNull(folder)
		title = "${library.name} / ${getString(R.string.lbl_family)}"
		val labels = listOf(R.string.lbl_family_genres, R.string.lbl_family_tags, R.string.lbl_family_ratings)
		AutomaticFolders.familyRequests(library).forEachIndexed { index, query ->
			rows.add(BrowseRowDef(getString(labels[index]), query, 40))
		}
		rowLoader.loadRows(rows)
	}
}
