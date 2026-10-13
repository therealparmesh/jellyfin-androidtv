// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChangedBy

/** Preserve new events while suppressing the action replayed when a host's lifecycle restarts. */
internal fun NavigationRepository.actionsWhileStarted(lifecycle: Lifecycle) = currentAction
	.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
	.distinctUntilChangedBy { it.id }
