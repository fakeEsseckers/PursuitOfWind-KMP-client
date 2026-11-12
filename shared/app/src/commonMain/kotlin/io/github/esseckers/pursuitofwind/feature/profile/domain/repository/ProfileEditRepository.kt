package io.github.esseckers.pursuitofwind.feature.profile.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * The rider's saved profile and saving a new one, the app's language, and whether the app tells the rider about new
 * session windows. The cache is the source of truth; the form on screen is a draft.
 */
internal interface ProfileEditRepository {
    /** The saved profile, null while the rider has none. Emits again after every save. */
    val profile: Flow<Profile?>

    /** The app's language, a choice of this device that applies at once. */
    val language: Flow<AppLanguage>

    /** Whether the rider asked to be told about new session windows; a choice of this device too. */
    val alerts: Flow<Boolean>

    /** Saves [profile] on the server and in the cache. Never throws, apart from cancellation. */
    suspend fun save(profile: Profile): Outcome<Profile>

    suspend fun setLanguage(language: AppLanguage): Outcome<Unit>

    suspend fun setAlerts(on: Boolean): Outcome<Unit>
}
