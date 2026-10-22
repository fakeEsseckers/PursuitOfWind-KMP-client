package io.github.esseckers.pursuitofwind.data.internal.local

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.data.model.Cached
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Instant

/**
 * Keeps the cache tied to one user. The spot catalog and forecasts are the same for everyone and stay; followed spots,
 * crews, rides, feed positions and the profile belong to the user who fetched them. They are dropped when another
 * user signs in, and when the server created the user just now ([newUser], `201`): then whatever the cache holds was
 * fetched from a server that has forgotten it (the fake backend after a restart hands out the same ids again).
 */
internal class UserCache(
    private val database: PursuitOfWindDatabase,
    private val settings: LocalSettings,
) {
    suspend fun claimFor(
        userId: String,
        newUser: Boolean,
    ) {
        if (!newUser && settings.cacheOwner() == userId) return
        database.withWriteTransaction {
            database.spots().deleteMySpots()
            database.crews().deleteCrews()
            database.crews().deleteAllMembers()
            database.rides().deleteAll()
            database.syncState().deleteUserState()
        }
        settings.switchOwner(userId)
    }
}

/** [values] with the time [key] was last fetched. */
internal fun <T> SyncStateDao.cached(
    key: String,
    values: Flow<T>,
): Flow<Cached<T>> = combine(values, observe(key)) { value, state -> Cached(value, state?.updatedAt?.let(Instant::fromEpochMilliseconds)) }

internal suspend fun SyncStateDao.markUpdated(
    key: String,
    at: Instant,
) {
    upsert(SyncStateEntity(key, at.toEpochMilliseconds(), get(key)?.cursor))
}
