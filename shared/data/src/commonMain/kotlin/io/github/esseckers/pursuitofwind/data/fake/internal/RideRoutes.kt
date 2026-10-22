package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RideCancelledEventDto
import io.github.esseckers.pursuitofwind.contract.RideCreatedEventDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideListDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.SeatReleasedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import io.ktor.http.HttpStatusCode

/** Rides and seats. Every change bumps the ride's version and goes into the crew feed. */
internal class RideRoutes(
    private val state: FakeState,
) {
    fun list(call: FakeCall): FakeResponse {
        val crew = state.crew(call.pathParameter("crewId")).also { state.requireMember(it, call.userId) }
        val from = call.instantQuery("from", call.now)
        val rides =
            state.rides.values
                .filter { it.crewId == crew.id && it.plan.windowEnd > from }
                .sortedBy { it.plan.departAt }
        return json(RideListDto.serializer(), RideListDto(rides.map(state::rideDto)))
    }

    fun create(call: FakeCall): FakeResponse {
        val crew = state.crew(call.pathParameter("crewId")).also { state.requireMember(it, call.userId) }
        val request = call.decode(CreateRideRequestDto.serializer())
        check422(request.spotId in state.spots) { "spotId ${request.spotId} is not in the catalog" }
        Validation.ride(request)
        val ride = Ride(state.nextId("rid"), crew.id, call.userId, request, call.now)
        state.rides[ride.id] = ride
        state.addEvent(crew) { id -> RideCreatedEventDto(id, crew.id, call.now, state.person(call.userId), state.rideDto(ride)) }
        return json(RideDto.serializer(), state.rideDto(ride), HttpStatusCode.Created)
    }

    fun get(call: FakeCall): FakeResponse = json(RideDto.serializer(), state.rideDto(memberRide(call).first))

    /** Driver only. Cancelling twice returns the cancelled ride. */
    fun cancel(call: FakeCall): FakeResponse {
        val (ride, crew) = memberRide(call)
        if (ride.driverId != call.userId) {
            throw problem(HttpStatusCode.Forbidden, ProblemCode.NOT_THE_DRIVER, "Only the driver can cancel this ride")
        }
        if (ride.status == RideStatusDto.SCHEDULED) {
            ride.status = RideStatusDto.CANCELLED
            ride.version++
            state.addEvent(crew) { id -> RideCancelledEventDto(id, crew.id, call.now, state.person(call.userId), ride.id, ride.version) }
        }
        return json(RideDto.serializer(), state.rideDto(ride))
    }

    fun takeSeat(call: FakeCall): FakeResponse {
        val (ride, crew) = memberRide(call)
        val refusal =
            when {
                ride.status == RideStatusDto.CANCELLED -> {
                    conflict(ProblemCode.RIDE_CANCELLED, "The ride was cancelled")
                }

                ride.driverId == call.userId || call.userId in ride.passengerIds -> {
                    conflict(
                        ProblemCode.ALREADY_JOINED,
                        "Already in this ride",
                    )
                }

                ride.seatsFree == 0 -> {
                    conflict(ProblemCode.SEATS_FULL, "No seats left", "Ride ${ride.id} has no free seats.")
                }

                else -> {
                    null
                }
            }
        if (refusal != null) throw refusal
        ride.passengerIds += call.userId
        ride.version++
        state.addEvent(crew) { id ->
            SeatTakenEventDto(id, crew.id, call.now, state.person(call.userId), ride.id, ride.version, ride.seatsFree)
        }
        return json(RideDto.serializer(), state.rideDto(ride), HttpStatusCode.Created)
    }

    /** Releasing a seat the rider does not have changes nothing. */
    fun releaseSeat(call: FakeCall): FakeResponse {
        val (ride, crew) = memberRide(call)
        if (ride.passengerIds.remove(call.userId)) {
            ride.version++
            state.addEvent(crew) { id ->
                SeatReleasedEventDto(id, crew.id, call.now, state.person(call.userId), ride.id, ride.version, ride.seatsFree)
            }
        }
        return json(RideDto.serializer(), state.rideDto(ride))
    }

    private fun memberRide(call: FakeCall): Pair<Ride, Crew> {
        val ride = state.ride(call.pathParameter("rideId"))
        val crew = state.crew(ride.crewId)
        state.requireMember(crew, call.userId)
        return ride to crew
    }

    private fun conflict(
        code: String,
        title: String,
        detail: String? = null,
    ): ProblemException = problem(HttpStatusCode.Conflict, code, title, detail)
}
