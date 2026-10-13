// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.settings.screen.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import org.jellyfin.androidtv.preference.LibraryPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository
import kotlinx.coroutines.flow.map
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun rememberUserView(itemId: UUID): BaseItemDto? {
	val userViewsRepository = koinInject<UserViewsRepository>()
	val userView by remember(itemId, userViewsRepository) {
		userViewsRepository.views.map { views -> views.firstOrNull { view -> view.id == itemId } }
	}.collectAsState(null)
	return userView
}

@Composable
fun rememberLibraryPreferences(displayPreferencesId: String): LibraryPreferences? {
	val repository = koinInject<PreferencesRepository>()
	return key(displayPreferencesId, repository) {
		produceState<LibraryPreferences?>(null) {
			value = repository.loadLibraryPreferences(displayPreferencesId)
		}.value
	}
}
