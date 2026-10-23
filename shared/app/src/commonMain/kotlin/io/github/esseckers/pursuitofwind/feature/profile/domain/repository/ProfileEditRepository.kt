package io.github.esseckers.pursuitofwind.feature.profile.domain.repository

import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import kotlinx.coroutines.flow.Flow

/** The rider's saved profile, and saving a new one. The cache is the source of truth; the form on screen is a draft. */
internal interface ProfileEditRepository {
    /** The saved profile, null while the rider has none. Emits again after every save. */
    val profile: Flow<Profile?>

    /** Saves [profile] on the server and in the cache. Never throws, apart from cancellation. */
    suspend fun save(profile: Profile): Outcome<Profile>
}
