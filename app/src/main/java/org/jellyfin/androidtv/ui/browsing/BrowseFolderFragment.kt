// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.GridButtonPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import timber.log.Timber

abstract class BrowseFolderFragment : BrowseSupportFragment(), RowLoader {
	protected var folder: BaseItemDto? = null
	protected var includeType: String? = null

	protected var rows = mutableListOf<BrowseRowDef>()
	private var setupJob: Job? = null

	private val backgroundService by inject<BackgroundService>()
	private val itemLauncher by inject<ItemLauncher>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Parse intent
		folder = Json.decodeFromString<BaseItemDto>(arguments?.getString(Extras.Folder)!!)
		includeType = arguments?.getString(Extras.IncludeType)

		// Set BrowseSupportFragment properties
		title = folder?.name
		headersState = HEADERS_DISABLED

		// Add event listeners
		onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder?, item: Any?, _: RowPresenter.ViewHolder?, row: Row ->
			if (item is AutomaticFolderButton) {
				item.open()
			} else if (item is BaseRowItem) {
				itemLauncher.launch(
					item,
					(row as ListRow).adapter as ItemRowAdapter,
					requireContext()
				)
			}
		}
		onItemViewSelectedListener = OnItemViewSelectedListener { _: Presenter.ViewHolder?, item: Any?, _: RowPresenter.ViewHolder?, row: Row ->
			if (item !is BaseRowItem) {
				backgroundService.clearBackgrounds()
			} else {
				val adapter = (row as? ListRow)?.adapter
				if (adapter is ItemRowAdapter) adapter.loadMoreItemsIfNeeded(adapter.indexOf(item))

				backgroundService.setBackground(item.baseItem)
			}
		}

	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		startQueries()
	}

	private fun startQueries() {
		// Tie setup to the view so navigation cancels requests and rebuilding a view cannot duplicate rows.
		setupJob?.cancel()
		setupJob = viewLifecycleOwner.lifecycleScope.launch {
			try {
				rows.clear()
				setupQueries(this@BrowseFolderFragment)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				Timber.w(error, "Failed to set up folder queries")
				val retryAdapter = ArrayObjectAdapter(GridButtonPresenter(155, 140, 40)).apply {
					add(AutomaticFolderButton(getString(R.string.lbl_browse_retry)) { startQueries() })
				}
				adapter = ArrayObjectAdapter(PositionableListRowPresenter()).apply {
					add(ListRow(HeaderItem(title?.toString()), retryAdapter))
				}
			}
		}
	}

	protected abstract suspend fun setupQueries(rowLoader: RowLoader)

	override fun loadRows(rows: MutableList<BrowseRowDef>) {
		val mutableAdapter = MutableObjectAdapter<Row>(PositionableListRowPresenter()).also {
			adapter = it
		}

		val cardPresenter = CardPresenter()

		for (def in rows) {
			ItemRowAdapter(
				requireContext(),
				def.query,
				def.chunkSize,
				def.preferParentThumb,
				def.isStaticHeight,
				cardPresenter,
				mutableAdapter,
				def.queryType
			).apply {
				requestLifecycleOwner = viewLifecycleOwner
				val row = ListRow(HeaderItem(def.headerText), this)
				setReRetrieveTriggers(def.changeTriggers)
				setRow(row)
				mutableAdapter.add(row)
				Retrieve()
			}
		}
	}
}
