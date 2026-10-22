package io.github.esseckers.pursuitofwind.data.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Instant

public data class Crew(
    val id: String,
    val name: String,
    val inviteCode: String,
    val memberCount: Int,
    val createdAt: Instant,
)

public enum class MemberRole {
    OWNER,
    MEMBER,
}

public data class CrewMember(
    val userId: String,
    val displayName: String,
    val role: MemberRole,
    val joinedAt: Instant,
)

/** Someone as other crew members see them. */
public data class Person(
    val userId: String,
    val displayName: String,
)

public enum class RideStatus {
    SCHEDULED,
    CANCELLED,
}

/**
 * A ride a crew member offers. [version] grows with every change on the server; the cache keeps the highest one.
 * [pending] is true while a seat change made on this device waits for the server's answer.
 */
public data class Ride(
    val id: String,
    val version: Int,
    val crewId: String,
    val spotId: SpotId,
    val driver: Person,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
    val meetingPoint: String,
    val seatsTotal: Int,
    val seatsFree: Int,
    val passengers: List<Person>,
    val status: RideStatus,
    val createdAt: Instant,
    val pending: Boolean = false,
)

/** What a driver posts to offer a ride. [seatsTotal] counts passenger seats. */
public data class RidePlan(
    val spotId: SpotId,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
    val meetingPoint: String,
    val seatsTotal: Int,
)
