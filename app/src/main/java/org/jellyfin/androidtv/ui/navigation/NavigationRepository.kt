// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.Stack

/**
 * Repository for app navigation. This manages the screens/pages for the app.
 */
interface NavigationRepository {
	/**
	 * The current action to act on.
	 *
	 * @see NavigationAction
	 */
	val currentAction: SharedFlow<NavigationAction>

	/**
	 * Navigate to [destination].
	 *
	 * @see Destinations
	 */
	fun navigate(destination: Destination) = navigate(destination, false)

	/**
	 * Navigate to [destination].
	 *
	 * @see Destinations
	 */
	fun navigate(destination: Destination, replace: Boolean)

	/**
	 * Whether the [goBack] function will succeed or not.
	 *
	 * @see [goBack]
	 */
	val canGoBack: Boolean

	/**
	 * Go back to the previous fragment. The back stack does not consider other destination types.
	 *
	 * @see [canGoBack]
	 */
	fun goBack(): Boolean

	/**
	 * Reset navigation to the initial destination or a specific [Destination.Fragment].
	 *
	 * @param clearHistory Empty out the back stack
	 */
	fun reset(destination: Destination.Fragment? = null, clearHistory: Boolean)

	/**
	 * Reset navigation to the initial destination or a specific [Destination.Fragment] without clearing history.
	 */
	fun reset(destination: Destination.Fragment? = null) = reset(destination, false)
}

class NavigationRepositoryImpl(
	private val defaultDestination: Destination.Fragment,
) : NavigationRepository {
	private val fragmentHistory = Stack<Destination.Fragment>()

	private var nextActionId = 0L
	// Active collectors receive every action. Storage grows on demand; inactive collectors need only the latest action.
	private val _currentAction = MutableSharedFlow<NavigationAction>(replay = 1, extraBufferCapacity = Int.MAX_VALUE - 1)
	override val currentAction = _currentAction.asSharedFlow()

	override fun navigate(destination: Destination, replace: Boolean) {
		Timber.i("Navigating to $destination (via navigate function)")
		val action = when (destination) {
			is Destination.Fragment -> NavigationAction.NavigateFragment(destination, true, replace, false, ++nextActionId)
		}
		if (destination is Destination.Fragment) {
			if (replace) {
				if (fragmentHistory.isNotEmpty()) fragmentHistory[fragmentHistory.lastIndex] = destination
			} else fragmentHistory.push(destination)
		}
		_currentAction.tryEmit(action)
	}

	override val canGoBack: Boolean get() = fragmentHistory.isNotEmpty()

	override fun goBack(): Boolean {
		if (fragmentHistory.empty()) return false

		Timber.i("Navigating back")
		fragmentHistory.pop()
		_currentAction.tryEmit(NavigationAction.GoBack(++nextActionId))
		return true
	}

	override fun reset(destination: Destination.Fragment?, clearHistory: Boolean) {
		val actualDestination = destination ?: defaultDestination
		if (clearHistory) fragmentHistory.clear()
		else fragmentHistory.push(actualDestination)
		_currentAction.tryEmit(NavigationAction.NavigateFragment(actualDestination, true, false, clearHistory, ++nextActionId))
		Timber.i("Navigating to $actualDestination (via reset, clearHistory=$clearHistory)")
	}
}

