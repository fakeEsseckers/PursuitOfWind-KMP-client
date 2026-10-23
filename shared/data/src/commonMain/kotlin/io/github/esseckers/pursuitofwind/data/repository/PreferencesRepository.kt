package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Choices that belong to this device and never go to the server: the app's language. They stay when another user's
 * data replaces the cache.
 */
public class PreferencesRepository internal constructor(
    private val settings: LocalSettings,
) {
    /** The chosen language; [AppLanguage.SYSTEM] until the rider picks one. */
    public val language: Flow<AppLanguage> = settings.language

    public suspend fun setLanguage(language: AppLanguage): Outcome<Unit> = outcome { settings.saveLanguage(language) }
}
