package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.data.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * What the app keeps to tell the rider about a new session window on its own: whether the rider asked for it, a choice
 * of this device like the language, and the windows already announced, which belong to the user and go when another
 * user's data replaces the cache. Nothing here goes to the server: until it sends pushes, the app checks by itself.
 */
public class WindowAlertsRepository internal constructor(
    private val settings: LocalSettings,
) {
    /** Whether the rider asked to be told about new windows; false until the rider turns it on. */
    public val enabled: Flow<Boolean> = settings.windowAlerts

    public suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = outcome { settings.saveWindowAlerts(enabled) }

    /** The windows announced so far, as [saveNotified] last stored them. */
    public suspend fun notified(): Outcome<List<NotifiedWindow>> = outcome { settings.notifiedWindows() }

    /** Replaces the announced windows with [windows]; the caller drops the ones that are over. */
    public suspend fun saveNotified(windows: List<NotifiedWindow>): Outcome<Unit> = outcome { settings.saveNotifiedWindows(windows) }
}
