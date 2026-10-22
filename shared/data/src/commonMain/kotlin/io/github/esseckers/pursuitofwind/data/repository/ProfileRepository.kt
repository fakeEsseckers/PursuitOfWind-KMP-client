package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.mapping.toDto
import io.github.esseckers.pursuitofwind.data.internal.mapping.toProfile
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.internal.newIdempotencyKey
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** The rider's profile, kept in DataStore so it is there offline. */
public class ProfileRepository internal constructor(
    private val api: PursuitOfWindApi,
    private val settings: LocalSettings,
    private val clock: Clock,
) {
    /**
     * The cached profile. [Cached.value] is null when the rider has no profile yet (a new user goes to onboarding);
     * [Cached.updatedAt] is null when it was never fetched.
     */
    public val profile: Flow<Cached<Profile?>> = settings.profile.map { stored -> Cached(stored?.profile?.toProfile(), stored?.updatedAt) }

    public suspend fun refresh(): Outcome<Unit> = outcome { settings.saveProfile(api.profile(), clock.now()) }

    public suspend fun save(profile: Profile): Outcome<Profile> =
        outcome {
            val saved = api.putProfile(profile.toDto(), newIdempotencyKey())
            settings.saveProfile(saved, clock.now())
            saved.toProfile()
        }
}
