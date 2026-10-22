package io.github.esseckers.pursuitofwind.data.internal.sync

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.contract.CrewEventDto
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.MemberJoinedEventDto
import io.github.esseckers.pursuitofwind.contract.PersonDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
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
import io.github.esseckers.pursuitofwind.data.internal.local.SyncStateEntity
import io.github.esseckers.pursuitofwind.data.internal.local.markUpdated
import io.github.esseckers.pursuitofwind.data.internal.mapping.encodePeople
import io.github.esseckers.pursuitofwind.data.internal.mapping.people
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import io.github.esseckers.pursuitofwind.data.internal.network.ApiException
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Keeps a crew current from its event feed, through [store].
 *
 * Without a position yet, or when the server answers `410 FEED_RESET`, it starts over: it remembers the newest event
 * first and then reloads members and rides, so no event between the two calls is lost (events are applied by version,
 * so seeing one twice is harmless). Syncs run one at a time, so a sync from the UI and one from a background task
 * cannot interleave.
 */
internal class CrewFeedSync(
    private val api: PursuitOfWindApi,
    private val database: PursuitOfWindDatabase,
    private val store: CrewStore,
    private val clock: Clock,
    private val pageSize: Int = PAGE_SIZE,
) {
    private val crews = database.crews()
    private val rides = database.rides()
    private val sync = database.syncState()
    private val syncing = Mutex()

    /** Applies the feed events after the last one applied. */
    suspend fun sync(crewId: String) {
        syncing.withLock {
            val cursor = sync.get(SyncKeys.feed(crewId))?.cursor
            if (cursor == null) resync(crewId) else catchUp(crewId, cursor)
        }
    }

    private suspend fun resync(crewId: String) {
        val newest = api.feed(crewId, since = null, limit = 1).lastEventId
        store.refreshMembers(crewId)
        store.refreshRides(crewId)
        saveCursor(crewId, newest)
    }

    private suspend fun catchUp(
        crewId: String,
        cursor: String,
    ) {
        var since: String? = cursor.ifEmpty { null }
        do {
            val fromEmptyFeed = since == null
            val page = page(crewId, since) ?: return resync(crewId)
            // Without `since` the server sends only the newest events: a full page may hide older ones.
            if (fromEmptyFeed && page.items.size >= pageSize) return resync(crewId)
            val missing = fetchMissingRides(page)
            database.withWriteTransaction {
                missing.forEach { store.putNewer(it.toEntity()) }
                page.items.forEach { apply(it) }
                since = page.lastEventId ?: since
                saveCursor(crewId, since)
            }
        } while (page.hasMore && page.lastEventId != null)
        database.withWriteTransaction {
            sync.markUpdated(SyncKeys.members(crewId), clock.now())
            sync.markUpdated(SyncKeys.rides(crewId), clock.now())
        }
    }

    /** A page of events after [since]; null when the server reset the feed. */
    private suspend fun page(
        crewId: String,
        since: String?,
    ): CrewFeedDto? =
        try {
            api.feed(crewId, since, pageSize)
        } catch (e: ApiException) {
            if (e.code != ProblemCode.FEED_RESET) throw e
            null
        }

    /**
     * Rides that seat events of [page] refer to but the cache does not hold, fetched before the page is applied, so
     * the cursor never moves past an event whose ride could not be loaded.
     */
    private suspend fun fetchMissingRides(page: CrewFeedDto): List<RideDto> {
        val created =
            page.items
                .filterIsInstance<RideCreatedEventDto>()
                .map { it.ride.id }
                .toSet()
        return page.items
            .mapNotNull { it.rideId() }
            .distinct()
            .filter { it !in created && rides.ride(it) == null }
            .mapNotNull { rideOrNull(it) }
    }

    /** Null when the server refuses (`4xx`): such a ride cannot be shown, and must not block the feed forever. */
    private suspend fun rideOrNull(rideId: String): RideDto? =
        try {
            api.ride(rideId)
        } catch (e: ApiException) {
            if (e.status !in CLIENT_ERRORS) throw e
            null
        }

    /** Remembers the newest applied event; an empty string stands for "the feed was empty". */
    private suspend fun saveCursor(
        crewId: String,
        cursor: String?,
    ) {
        sync.upsert(SyncStateEntity(SyncKeys.feed(crewId), clock.now().toEpochMilliseconds(), cursor.orEmpty()))
    }

    private suspend fun apply(event: CrewEventDto) {
        when (event) {
            is RideCreatedEventDto -> {
                store.putNewer(event.ride.toEntity())
            }

            is SeatTakenEventDto -> {
                updateRide(event.rideId, event.rideVersion) {
                    it.copy(
                        seatsFree = event.seatsFree,
                        passengers = encodePeople((it.people() + event.actor).distinctBy(PersonDto::userId)),
                    )
                }
            }

            is SeatReleasedEventDto -> {
                updateRide(event.rideId, event.rideVersion) {
                    it.copy(
                        seatsFree = event.seatsFree,
                        passengers =
                            encodePeople(
                                it.people().filterNot { p ->
                                    p.userId ==
                                        event.actor.userId
                                },
                            ),
                    )
                }
            }

            is RideCancelledEventDto -> {
                updateRide(event.rideId, event.rideVersion) { it.copy(status = RideStatusDto.CANCELLED.name) }
            }

            is MemberJoinedEventDto -> {
                crews.upsertMembers(listOf(event.member.toEntity(event.crewId)))
                crews.setMemberCount(event.crewId, crews.countMembers(event.crewId))
            }

            is UnknownCrewEventDto -> {
                // A newer event type: nothing to apply, the cursor still moves past it.
            }
        }
    }

    /** Applies [change] to the server fields when [version] is newer; the pending overlay stays as it is. */
    private suspend fun updateRide(
        rideId: String,
        version: Int,
        change: (RideEntity) -> RideEntity,
    ) {
        val ride = rides.ride(rideId) ?: return
        if (ride.version < version) rides.upsert(listOf(change(ride).copy(version = version)))
    }

    private fun CrewEventDto.rideId(): String? =
        when (this) {
            is SeatTakenEventDto -> rideId
            is SeatReleasedEventDto -> rideId
            is RideCancelledEventDto -> rideId
            else -> null
        }

    private companion object {
        const val PAGE_SIZE = 100
        val CLIENT_ERRORS = 400..499
    }
}
