package io.github.esseckers.pursuitofwind.app.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Moves through the [backStack]. Screens never touch it: each one gets lambdas such as `onOpenSpot` and `onBack`, which
 * call this. A destination that is already on top is not opened twice, so a double tap opens one screen.
 */
internal class Navigator(
    private val backStack: MutableList<NavKey>,
) {
    fun open(destination: Destination) {
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }

    /** Leaves the top screen; the first screen stays, and the system closes the app from there. */
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
