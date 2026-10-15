// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.navigation

/**
 * Emitted actions from the navigation repository.
 */
sealed interface NavigationAction {
	val id: Long
	/**
	 * Navigate to the fragment in [destination].
	 */
	data class NavigateFragment(
		val destination: Destination.Fragment,
		val addToBackStack: Boolean,
		val replace: Boolean,
		val clear: Boolean,
		override val id: Long = 0,
	) : NavigationAction

	/**
	 * Go back to the previous fragment manager state.
	 */
	data class GoBack(override val id: Long) : NavigationAction

}
