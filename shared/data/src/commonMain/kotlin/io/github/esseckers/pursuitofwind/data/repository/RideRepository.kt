package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.RideEntity
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.cached
import io.github.esseckers.pursuitofwind.data.internal.mapping.canBeTakenBy
import io.github.esseckers.pursuitofwind.data.internal.mapping.hasPassenger
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import io.github.esseckers.pursuitofwind.data.internal.mapping.toRide
import io.github.esseckers.pursuitofwind.data.internal.network.AuthSession
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.internal.newIdempotencyKey
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewStore
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Rides of a crew and seats in them.
 *
 * Taking or releasing a seat shows the expected result at once ([Ride.pending]) as an overlay on the server's copy of
 * the ride. When the request ends, however it ends (success, refusal, network error, cancellation), the overlay goes
 * and the server's state shows: the answer itself, or the ride reloaded after a refusal. A refusal comes back as the
 * error, for example `AppError.Conflict(SEATS_FULL)` when someone else took the last seat first; `ALREADY_JOINED`
 * counts as success, because the seat is the rider's.
 */
public class RideRepository internal constructor(
    private val api: PursuitOfWindApi,
    private val database: PursuitOfWindDatabase,
    private val store: CrewStore,
    private val session: AuthSession,
    private val settings: LocalSettings,
) {
    private val rides = database.rides()
    private val sync = database.syncState()

    /** One seat change at a time, so a double tap sends the second request only after the first was answered. */
    private val seatChanges = Mutex()

    /** Rides of [crewId] by departure, including cancelled ones (shown crossed out) until they end. */
    public fun rides(crewId: String): Flow<Cached<List<Ride>>> =
        sync.cached(SyncKeys.rides(crewId), rides.rides(crewId).map { list -> list.map { it.toRide() } })

    public suspend fun refresh(crewId: String): Outcome<Unit> = outcome { store.refreshRides(crewId) }

    public suspend fun offer(
        crewId: String,
        plan: RidePlan,
    ): Outcome<Ride> =
        outcome {
            val request =
                CreateRideRequestDto(
                    spotId = plan.spotId.value,
                    windowStart = plan.windowStart,
                    windowEnd = plan.windowEnd,
                    departAt = plan.departAt,
                    meetingPoint = plan.meetingPoint,
                    seatsTotal = plan.seatsTotal,
                )
            keep(api.createRide(crewId, request, newIdempotencyKey()))
        }

    /** Driver only; cancelling a cancelled ride returns it unchanged. */
    public suspend fun cancel(rideId: String): Outcome<Ride> = outcome { keep(api.cancelRide(rideId, newIdempotencyKey())) }

    public suspend fun takeSeat(rideId: String): Outcome<Ride> = changeSeat(rideId, take = true)

    /** Releasing a seat the rider does not have changes nothing. */
    public suspend fun releaseSeat(rideId: String): Outcome<Ride> = changeSeat(rideId, take = false)

    private suspend fun changeSeat(
        rideId: String,
        take: Boolean,
    ): Outcome<Ride> =
        seatChanges.withLock {
            try {
                markPending(rideId, take)
                val key = newIdempotencyKey()
                when (val result = outcome { keep(if (take) api.takeSeat(rideId, key) else api.releaseSeat(rideId, key)) }) {
                    is Outcome.Success -> result
                    is Outcome.Failure -> afterRefusal(rideId, result)
                }
            } finally {
                // Unconditionally: a cancellation can arrive after the overlay was written but before markPending
                // returned. Only this path writes overlays, and seatChanges lets one run at a time.
                withContext(NonCancellable) { rides.clearPending(rideId) }
            }
        }

    /**
     * Shows the change at once if it can work on the ride as cached. Nothing to show when the ride is unknown, the
     * rider is not signed in yet, or the change would not do anything.
     */
    private suspend fun markPending(
        rideId: String,
        take: Boolean,
    ) {
        val userId = session.userId.value ?: return
        val name =
            settings.profile
                .first()
                ?.profile
                ?.displayName
                .orEmpty()
        database.withWriteTransaction {
            val ride = rides.ride(rideId)
            val possible = ride != null && ride.pendingSeat == null && if (take) ride.canBeTakenBy(userId) else ride.hasPassenger(userId)
            if (ride != null && possible) {
                val change = if (take) RideEntity.TAKE else RideEntity.RELEASE
                rides.upsert(listOf(ride.copy(pendingSeat = change, pendingUserId = userId, pendingUserName = name)))
            }
        }
    }

    /**
     * After the server refused, reloads the ride so the cache shows what the server has. A network failure says
     * nothing about the server, so the cache stays. `ALREADY_JOINED` means the seat is the rider's: success.
     */
    private suspend fun afterRefusal(
        rideId: String,
        failure: Outcome.Failure,
    ): Outcome<Ride> {
        if (failure.error == AppError.Network) return failure
        val reloaded = outcome { keep(api.ride(rideId)) }
        val alreadySeated = failure.error == AppError.Conflict(ProblemCode.ALREADY_JOINED) && reloaded is Outcome.Success
        return if (alreadySeated) reloaded else failure
    }

    private suspend fun keep(ride: RideDto): Ride {
        store.upsertNewer(ride)
        return ride.toEntity().toRide()
    }
}
