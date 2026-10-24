package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.RideCreatedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatReleasedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import kotlin.time.Instant

// Changes to rides shared by the routes (a rider's request) and CrewLife (a crew member acting): each one bumps the
// ride's version where it changes and goes into the crew feed.

/** Posts a ride [driverId] offers in [crew] and announces it in the crew feed. */
internal fun FakeState.offerRide(
    crew: Crew,
    driverId: String,
    plan: CreateRideRequestDto,
    at: Instant,
): Ride {
    val ride = Ride(nextId("rid"), crew.id, driverId, plan, at)
    rides[ride.id] = ride
    addEvent(crew) { id -> RideCreatedEventDto(id, crew.id, at, person(driverId), rideDto(ride)) }
    return ride
}

/** Gives [userId] a seat in [ride], which must have one free. */
internal fun FakeState.takeSeat(
    ride: Ride,
    crew: Crew,
    userId: String,
    at: Instant,
) {
    ride.passengerIds += userId
    ride.version++
    addEvent(crew) { id -> SeatTakenEventDto(id, crew.id, at, person(userId), ride.id, ride.version, ride.seatsFree) }
}

/** Frees the seat of [userId] in [ride]; false if they had none. */
internal fun FakeState.releaseSeat(
    ride: Ride,
    crew: Crew,
    userId: String,
    at: Instant,
): Boolean {
    val released = ride.passengerIds.remove(userId)
    if (released) {
        ride.version++
        addEvent(crew) { id -> SeatReleasedEventDto(id, crew.id, at, person(userId), ride.id, ride.version, ride.seatsFree) }
    }
    return released
}
