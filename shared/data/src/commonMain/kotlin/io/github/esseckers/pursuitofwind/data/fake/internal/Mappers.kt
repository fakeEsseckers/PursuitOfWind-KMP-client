package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.CrewDto
import io.github.esseckers.pursuitofwind.contract.CrewMemberDto
import io.github.esseckers.pursuitofwind.contract.RideDto

/**
 * State → contract DTOs. Names come from the profiles at the time of the response; feed events keep the names they
 * were created with.
 */
internal fun FakeState.crewDto(crew: Crew): CrewDto = CrewDto(crew.id, crew.name, crew.inviteCode, crew.members.size, crew.createdAt)

internal fun FakeState.memberDto(member: Member): CrewMemberDto =
    CrewMemberDto(member.userId, displayName(member.userId), member.role, member.joinedAt)

internal fun FakeState.rideDto(ride: Ride): RideDto =
    RideDto(
        id = ride.id,
        version = ride.version,
        crewId = ride.crewId,
        spotId = ride.plan.spotId,
        driver = person(ride.driverId),
        windowStart = ride.plan.windowStart,
        windowEnd = ride.plan.windowEnd,
        departAt = ride.plan.departAt,
        meetingPoint = ride.plan.meetingPoint,
        seatsTotal = ride.plan.seatsTotal,
        seatsFree = ride.seatsFree,
        passengers = ride.passengerIds.map(::person),
        status = ride.status,
        createdAt = ride.createdAt,
    )
