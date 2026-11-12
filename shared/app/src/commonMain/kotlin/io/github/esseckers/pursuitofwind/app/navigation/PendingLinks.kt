package io.github.esseckers.pursuitofwind.app.navigation

import io.github.esseckers.pursuitofwind.core.link.AppLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A link into the app that waits to be opened. A link can arrive before the app shows anything, or while a new rider
 * is still setting up; it waits here until the navigation can open it. One link waits at a time: a newer one takes its
 * place, as the rider's latest tap is what they mean.
 */
internal class PendingLinks {
    private val waiting = MutableStateFlow<AppLink?>(null)

    /** The link that waits, null when none does. */
    val link: StateFlow<AppLink?> = waiting.asStateFlow()

    /** Takes [uri] when it is a link the app knows, and says whether it did. */
    fun open(uri: String): Boolean {
        val link = AppLink.parse(uri)
        if (link != null) waiting.value = link
        return link != null
    }

    /** [link] was opened; a newer link that took its place meanwhile keeps waiting. */
    fun opened(link: AppLink) {
        waiting.compareAndSet(link, null)
    }
}
