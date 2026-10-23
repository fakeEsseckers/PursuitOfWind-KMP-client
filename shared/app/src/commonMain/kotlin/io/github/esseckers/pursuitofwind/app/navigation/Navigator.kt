package io.github.esseckers.pursuitofwind.app.navigation

import androidx.navigation3.runtime.NavKey
import io.github.esseckers.pursuitofwind.data.model.Profile

/**
 * Moves through the [backStack]. Screens never touch it: each one gets lambdas such as `onOpenSpot` and `onBack`, which
 * call this. A destination that is already on top is not opened twice, so a double tap opens one screen.
 *
 * There is one back stack. Today is its root once the rider is set up, and every other tab sits on top of Today, so
 * back from a tab returns to Today and back from Today leaves the app.
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

    /** Shows the tab [tab]: Today alone, or [tab] on top of Today. Screens opened on top of the old tab close. */
    fun selectTab(tab: Destination) {
        show(if (tab == Destination.Today) listOf(Destination.Today) else listOf(Destination.Today, tab))
    }

    /** Ends onboarding, or the spot picker opened from Today: Today becomes the only screen. */
    fun finishSetup() {
        show(listOf(Destination.Today))
    }

    /**
     * Makes the back stack [target]. Entries that already match keep their state; the stack is never empty in between,
     * because the new entries are added before the old ones go.
     */
    private fun show(target: List<Destination>) {
        val keep = backStack.zip(target).takeWhile { (current, wanted) -> current == wanted }.size
        val added = target.drop(keep)
        backStack.addAll(added)
        repeat(backStack.size - keep - added.size) { backStack.removeAt(keep) }
    }
}

/** Where the app starts on a fresh launch: Welcome for a rider without a profile, Today for everyone else. */
internal fun startDestination(profile: Profile?): Destination = if (profile == null) Destination.Welcome else Destination.Today
