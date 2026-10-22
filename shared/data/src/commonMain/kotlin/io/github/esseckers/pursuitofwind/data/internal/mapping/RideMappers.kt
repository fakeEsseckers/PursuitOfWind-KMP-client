package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.PersonDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.data.internal.local.RideEntity
import io.github.esseckers.pursuitofwind.data.model.Person
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Instant

// DTO → cache entity → domain. DTOs never leave the data layer.

private val PEOPLE = ListSerializer(PersonDto.serializer())

internal fun RideDto.toEntity(): RideEntity =
    RideEntity(
        id = id,
        version = version,
        crewId = crewId,
        spotId = spotId,
        driverId = driver.userId,
        driverName = driver.displayName,
        windowStart = windowStart.toEpochMilliseconds(),
        windowEnd = windowEnd.toEpochMilliseconds(),
        departAt = departAt.toEpochMilliseconds(),
        meetingPoint = meetingPoint,
        seatsTotal = seatsTotal,
        seatsFree = seatsFree,
        passengers = encodePeople(passengers),
        status = status.name,
        createdAt = createdAt.toEpochMilliseconds(),
    )

/** The ride as the rider should see it: the server's copy with this device's pending seat change applied. */
internal fun RideEntity.toRide(): Ride {
    val (seats, people) = withPendingSeat()
    return Ride(
        id = id,
        version = version,
        crewId = crewId,
        spotId = SpotId(spotId),
        driver = Person(driverId, driverName),
        windowStart = Instant.fromEpochMilliseconds(windowStart),
        windowEnd = Instant.fromEpochMilliseconds(windowEnd),
        departAt = Instant.fromEpochMilliseconds(departAt),
        meetingPoint = meetingPoint,
        seatsTotal = seatsTotal,
        seatsFree = seats,
        passengers = people.map { Person(it.userId, it.displayName) },
        status = RideStatus.valueOf(status),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        pending = pendingSeat != null,
    )
}

/** Free seats and passengers once the pending change lands; unchanged when the server already shows it. */
private fun RideEntity.withPendingSeat(): Pair<Int, List<PersonDto>> {
    val people = people()
    val userId = pendingUserId
    val seated = people.any { it.userId == userId }
    return when {
        userId == null -> seatsFree to people
        pendingSeat == RideEntity.TAKE && !seated && seatsFree > 0 -> seatsFree - 1 to people + PersonDto(userId, pendingUserName.orEmpty())
        pendingSeat == RideEntity.RELEASE && seated -> seatsFree + 1 to people.filterNot { it.userId == userId }
        else -> seatsFree to people
    }
}

/** True if [userId] could take a seat in this ride as the server last described it. */
internal fun RideEntity.canBeTakenBy(userId: String): Boolean =
    status == RideStatusDto.SCHEDULED.name && seatsFree > 0 && driverId != userId && people().none { it.userId == userId }

internal fun RideEntity.hasPassenger(userId: String): Boolean = people().any { it.userId == userId }

internal fun RideEntity.people(): List<PersonDto> = ContractJson.decodeFromString(PEOPLE, passengers)

internal fun encodePeople(people: List<PersonDto>): String = ContractJson.encodeToString(PEOPLE, people)
