package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys

/**
 * Which cached data belongs to the signed-in user and which is the same for everyone, as [CacheOwnershipTest] checks
 * the database and `UserCache` against it: every table is in exactly one group, and another user signing in empties the
 * user's group and nothing else. `sync_state` holds keys of both kinds; [isSharedKey] tells them apart.
 */
internal object CacheOwnership {
    /** What one user fetched: the spots they follow, their crews, the crews' members and rides. */
    val USER_TABLES: Set<String> = setOf("my_spots", "crews", "members", "rides")

    /** The same for every user: the spot catalog and the forecasts. */
    val SHARED_TABLES: Set<String> = setOf("spots", "forecast_hours")

    /** Fetch times and feed cursors of both kinds. */
    const val MIXED_TABLE: String = "sync_state"

    /** True for the [SyncKeys] of shared data; every other key belongs to the user. */
    fun isSharedKey(key: String): Boolean = key == SyncKeys.CATALOG || key.startsWith(SyncKeys.FORECAST_PREFIX)
}
