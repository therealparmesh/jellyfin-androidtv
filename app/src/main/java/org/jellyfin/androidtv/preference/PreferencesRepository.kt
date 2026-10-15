// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.preference

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.collections.set

/**
 * Repository to access special preference stores.
 */
class PreferencesRepository(
	private val api: ApiClient,
	private val liveTvPreferences: LiveTvPreferences,
	private val userSettingPreferences: UserSettingPreferences,
) {
	private val libraryPreferences = mutableMapOf<String, LibraryPreferences>()

	private val loadMutex = Mutex()

	// Synchronous access is cache-only. UI callers must load before using saved values.
	fun getLibraryPreferences(preferencesId: String): LibraryPreferences =
		libraryPreferences.getOrPut(preferencesId) { LibraryPreferences(preferencesId, api) }

	suspend fun loadLibraryPreferences(preferencesId: String): LibraryPreferences = loadMutex.withLock {
		getLibraryPreferences(preferencesId).also { if (it.shouldUpdate) it.update() }
	}

	fun loadLibraryPreferencesAsync(preferencesId: String, owner: LifecycleOwner, onReady: (LibraryPreferences) -> Unit) =
		owner.lifecycleScope.launch { onReady(loadLibraryPreferences(preferencesId)) }

	suspend fun onSessionChanged() {
		// Note: Do not run parallel as the server can't deal with that
		// Relevant server issue: https://github.com/jellyfin/jellyfin/issues/5261
		liveTvPreferences.update()
		userSettingPreferences.update()

		loadMutex.withLock { libraryPreferences.clear() }
	}
}
