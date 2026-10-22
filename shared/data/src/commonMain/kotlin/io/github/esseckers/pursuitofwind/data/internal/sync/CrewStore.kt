package io.github.esseckers.pursuitofwind.data.internal.sync

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.RideEntity
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.markUpdated
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import kotlin.time.Clock

/**
 * Crew members and rides in the cache, loaded from the server here and kept current by [CrewFeedSync].
 *
 * Ride copies carry the server's `version`, and every write goes through [putNewer]: the cache only moves a ride
 * forward, so a late response, a reload that raced with a change, or a replayed event cannot undo a newer change.
 * Writes keep a pending seat change of this device (see [RideEntity]).
 */
internal class CrewStore(
    private val api: PursuitOfWindApi,
    private val database: PursuitOfWindDatabase,
    private val clock: Clock,
) {
    private val crews = database.crews()
    private val rides = database.rides()
    private val sync = database.syncState()

    /** Adds the server's members; members never leave a crew in `/v1`, so none are removed. */
    suspend fun refreshMembers(crewId: String) {
        val members = api.members(crewId).items
        database.withWriteTransaction {
            crews.upsertMembers(members.map { it.toEntity(crewId) })
            crews.setMemberCount(crewId, crews.countMembers(crewId))
            sync.markUpdated(SyncKeys.members(crewId), clock.now())
        }
    }

    /**
     * Brings the crew's rides to the server's list: newer copies win, and rides the server no longer lists (they ended)
     * go. Only rides cached before the request can go: one that arrived while the request was out (offered here, or
     * from the feed) is newer than the answer.
     */
    suspend fun refreshRides(crewId: String) {
        val now = clock.now()
        val cachedBefore = rides.rideIds(crewId)
        val fresh = api.rides(crewId, now).items
        val listed = fresh.map { it.id }.toSet()
        database.withWriteTransaction {
            rides.deleteRides(cachedBefore.filterNot { it in listed })
            fresh.forEach { putNewer(it.toEntity()) }
            sync.markUpdated(SyncKeys.rides(crewId), now)
        }
    }

    /** Stores [ride] unless the cache already holds a newer version. */
    suspend fun upsertNewer(ride: RideDto) {
        database.withWriteTransaction { putNewer(ride.toEntity()) }
    }

    /** [upsertNewer] for callers that already hold a write transaction. */
    suspend fun putNewer(ride: RideEntity) {
        val current = rides.ride(ride.id)
        if (current == null || ride.version >= current.version) rides.upsert(listOf(ride.withOverlayOf(current)))
    }
}
