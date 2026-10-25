package io.github.esseckers.pursuitofwind.data.internal.sync

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.contract.CrewEventDto
import io.github.esseckers.pursuitofwind.contract.CrewMemberDto
import io.github.esseckers.pursuitofwind.contract.MemberJoinedEventDto
import io.github.esseckers.pursuitofwind.contract.PersonDto
import io.github.esseckers.pursuitofwind.contract.RideCancelledEventDto
import io.github.esseckers.pursuitofwind.contract.RideCreatedEventDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.SeatReleasedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import io.github.esseckers.pursuitofwind.contract.UnknownCrewEventDto
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.RideEntity
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.markUpdated
import io.github.esseckers.pursuitofwind.data.internal.mapping.canBeTakenBy
import io.github.esseckers.pursuitofwind.data.internal.mapping.encodePeople
import io.github.esseckers.pursuitofwind.data.internal.mapping.hasPassenger
import io.github.esseckers.pursuitofwind.data.internal.mapping.people
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import kotlin.time.Instant

/**
 * Crew members and rides in the cache: every write of them goes through here, with the one read a write needs
 * ([rideIds]), and nothing here goes to the network ([CrewFeedSync] fetches).
 *
 * Ride copies carry the server's `version`, and the cache only moves a ride forward: a late response, a reload that
 * raced with a change, or a replayed event cannot undo a newer change. Writes keep a pending seat change of this
 * device (see [RideEntity]). Functions named `put…` run inside a write transaction the caller holds, so a feed page
 * lands at once; the others open their own.
 */
internal class CrewStore(
    private val database: PursuitOfWindDatabase,
) {
    private val crews = database.crews()
    private val rides = database.rides()
    private val sync = database.syncState()

    /** Adds [members] of [crewId], fetched [at]; members never leave a crew in `/v1`, so none are removed. */
    suspend fun saveMembers(
        crewId: String,
        members: List<CrewMemberDto>,
        at: Instant,
    ) {
        database.withWriteTransaction {
            members.forEach { putMember(crewId, it) }
            sync.markUpdated(SyncKeys.members(crewId), at)
        }
    }

    /** Adds [member] to [crewId] and counts the crew's members again. */
    suspend fun putMember(
        crewId: String,
        member: CrewMemberDto,
    ) {
        crews.upsertMembers(listOf(member.toEntity(crewId)))
        crews.setMemberCount(crewId, crews.countMembers(crewId))
    }

    /** The ids of the rides of [crewId] in the cache now. */
    suspend fun rideIds(crewId: String): List<String> = rides.rideIds(crewId)

    /**
     * Brings the rides of [crewId] to [fresh], the server's list fetched [at]: newer copies win, and rides the server no
     * longer lists (they ended) go. Only rides in [cachedBefore], the cache before the request, can go: one that
     * arrived while the request was out (offered here, or from the feed) is newer than the answer.
     */
    suspend fun saveRides(
        crewId: String,
        cachedBefore: List<String>,
        fresh: List<RideDto>,
        at: Instant,
    ) {
        val listed = fresh.map { it.id }.toSet()
        database.withWriteTransaction {
            rides.deleteRides(cachedBefore.filterNot { it in listed })
            fresh.forEach { putNewer(it.toEntity()) }
            sync.markUpdated(SyncKeys.rides(crewId), at)
        }
    }

    /** Stores [ride] unless the cache already holds a newer version, in a write transaction of its own. */
    suspend fun saveNewer(ride: RideDto) {
        database.withWriteTransaction { putNewer(ride.toEntity()) }
    }

    /**
     * Stores [ride] unless the cache already holds a newer version, inside the caller's write transaction. A copy as
     * new as the cached one replaces it: it is the same state of the ride. The pending seat change stays.
     */
    suspend fun putNewer(ride: RideEntity) {
        val current = rides.ride(ride.id)
        if (current == null || ride.version >= current.version) rides.upsert(listOf(ride.withOverlayOf(current)))
    }

    /**
     * Applies [change] to the server's fields of the cached ride [rideId] when [version] is newer than the cached one;
     * the pending overlay stays as it is. Nothing happens to a ride the cache does not hold.
     */
    suspend fun putChange(
        rideId: String,
        version: Int,
        change: (RideEntity) -> RideEntity,
    ) {
        val ride = rides.ride(rideId) ?: return
        if (ride.version < version) rides.upsert(listOf(change(ride).copy(version = version)))
    }

    /**
     * Shows that [userId] ([name]) is taking ([take]) or releasing a seat in [rideId], when that can work on the ride as
     * cached: not on a ride the cache lacks, one with a change already pending, or one where it would change nothing.
     */
    suspend fun markPending(
        rideId: String,
        userId: String,
        name: String,
        take: Boolean,
    ) {
        database.withWriteTransaction {
            val ride = rides.ride(rideId)
            val possible = ride != null && ride.pendingSeat == null && if (take) ride.canBeTakenBy(userId) else ride.hasPassenger(userId)
            if (ride != null && possible) {
                val change = if (take) RideEntity.TAKE else RideEntity.RELEASE
                rides.upsert(listOf(ride.copy(pendingSeat = change, pendingUserId = userId, pendingUserName = name)))
            }
        }
    }

    /** Applies a feed [event] (inside the page's transaction): rides only move forward, and new members are counted. */
    suspend fun putEvent(event: CrewEventDto) {
        when (event) {
            is RideCreatedEventDto -> {
                putNewer(event.ride.toEntity())
            }

            is SeatTakenEventDto -> {
                putChange(event.rideId, event.rideVersion) {
                    it.copy(
                        seatsFree = event.seatsFree,
                        passengers = encodePeople((it.people() + event.actor).distinctBy(PersonDto::userId)),
                    )
                }
            }

            is SeatReleasedEventDto -> {
                putChange(event.rideId, event.rideVersion) {
                    it.copy(
                        seatsFree = event.seatsFree,
                        passengers = encodePeople(it.people().filterNot { person -> person.userId == event.actor.userId }),
                    )
                }
            }

            is RideCancelledEventDto -> {
                putChange(event.rideId, event.rideVersion) { it.copy(status = RideStatusDto.CANCELLED.name) }
            }

            is MemberJoinedEventDto -> {
                putMember(event.crewId, event.member)
            }

            is UnknownCrewEventDto -> {
                // A newer event type: nothing to apply, the cursor still moves past it.
            }
        }
    }

    /** Drops the pending seat change of [rideId]: the server's copy shows again. */
    suspend fun clearPending(rideId: String) {
        rides.clearPending(rideId)
    }
}
