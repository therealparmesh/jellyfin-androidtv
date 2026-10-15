// Modified for the Parmesh fork on 2026-10-15. See FORK.md.
package org.jellyfin.androidtv.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

/** Stay subscribed while stopped so pending actions and repository history cannot diverge. */
internal fun NavigationRepository.actionsWhileStarted(lifecycle: Lifecycle) = currentAction
	.map { action -> lifecycle.withStarted { action } }
	.distinctUntilChangedBy { it.id }
