package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
public enum class RideStatusDto {
    SCHEDULED,
    CANCELLED,
}

/** `POST /v1/crews/{crewId}/rides`. [seatsTotal] counts passenger seats, the driver not included. */
@Serializable
public data class CreateRideRequestDto(
    val spotId: String,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
    val meetingPoint: String,
    val seatsTotal: Int,
)

/** [version] grows with every change; keep the copy with the highest version (feed events carry it too). */
@Serializable
public data class RideDto(
    val id: String,
    val version: Int,
    val crewId: String,
    val spotId: String,
    val driver: PersonDto,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
    val meetingPoint: String,
    val seatsTotal: Int,
    val seatsFree: Int,
    val passengers: List<PersonDto>,
    val status: RideStatusDto,
    val createdAt: Instant,
)

/** `GET /v1/crews/{crewId}/rides`. */
@Serializable
public data class RideListDto(
    val items: List<RideDto>,
)
