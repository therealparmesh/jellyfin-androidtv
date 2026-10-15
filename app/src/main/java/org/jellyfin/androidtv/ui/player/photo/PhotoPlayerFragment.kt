// Modified for the Parmesh fork on 2026-10-13. See FORK.md.
package org.jellyfin.androidtv.ui.player.photo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.BaseScreen
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.android.ext.android.inject
import timber.log.Timber

class PhotoPlayerFragment : Fragment() {
	companion object {
		const val ARGUMENT_ITEM_ID = "item_id"
		const val ARGUMENT_ALBUM_SORT_BY = "album_sort_by"
		const val ARGUMENT_ALBUM_SORT_ORDER = "album_sort_order"
		const val ARGUMENT_AUTO_PLAY = "auto_play"
	}

	private val viewModel by viewModel<PhotoPlayerViewModel>()
	private val navigationRepository by inject<NavigationRepository>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Load requested item in viewmodel
		lifecycleScope.launch {
			val itemId = requireNotNull(arguments?.getString(ARGUMENT_ITEM_ID)?.toUUIDOrNull())
			val albumSortBy = arguments?.getString(ARGUMENT_ALBUM_SORT_BY)?.let {
				ItemSortBy.Companion.fromNameOrNull(it)
			} ?: ItemSortBy.SORT_NAME
			val albumSortOrder = arguments?.getString(ARGUMENT_ALBUM_SORT_ORDER)?.let {
				SortOrder.Companion.fromNameOrNull(it)
			} ?: SortOrder.ASCENDING
			try {
				viewModel.loadItem(itemId, setOf(albumSortBy), albumSortOrder)
			} catch (error: ApiClientException) {
				Timber.w(error, "Unable to load the requested photo")
				Toast.makeText(requireContext(), R.string.msg_unable_to_load_photo, Toast.LENGTH_LONG).show()
				if (!navigationRepository.goBack()) navigationRepository.reset(Destinations.home, true)
				return@launch
			}

			val autoPlay = arguments?.getBoolean(ARGUMENT_AUTO_PLAY) == true
			if (autoPlay) viewModel.startPresentation()
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		BaseScreen {
			PhotoPlayerScreen()
		}
	}
}
