package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.sdk.model.api.BaseItemDto

class AutomaticFoldersFragment : BrowseSupportFragment() {
	private lateinit var library: BaseItemDto

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		library = Json.decodeFromString(requireArguments().getString(Extras.Folder)!!)
		title = library.name
		headersState = HEADERS_DISABLED
		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			(item as? AutomaticFolderButton)?.open()
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
			add(createAutomaticFoldersRow(library, viewLifecycleOwner))
		}
	}
}
