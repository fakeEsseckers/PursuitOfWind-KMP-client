package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Something that happened in a crew. The JSON `type` field selects the subtype; serialize events through this
 * interface's serializer (as [CrewFeedDto] does), otherwise `type` is left out. Event types are open within `/v1`:
 * [ContractJson] decodes types it does not know as [UnknownCrewEventDto].
 */
@Serializable
public sealed interface CrewEventDto {
    /** Opaque; pass the newest one as `since`. */
    public val id: String
    public val crewId: String
    public val occurredAt: Instant
    public val actor: PersonDto
}

@Serializable
@SerialName("RIDE_CREATED")
public data class RideCreatedEventDto(
    override val id: String,
    override val crewId: String,
    override val occurredAt: Instant,
    override val actor: PersonDto,
    val ride: RideDto,
) : CrewEventDto

@Serializable
@SerialName("SEAT_TAKEN")
public data class SeatTakenEventDto(
    override val id: String,
    override val crewId: String,
    override val occurredAt: Instant,
    override val actor: PersonDto,
    val rideId: String,
    val rideVersion: Int,
    val seatsFree: Int,
) : CrewEventDto

@Serializable
@SerialName("SEAT_RELEASED")
public data class SeatReleasedEventDto(
    override val id: String,
    override val crewId: String,
    override val occurredAt: Instant,
    override val actor: PersonDto,
    val rideId: String,
    val rideVersion: Int,
    val seatsFree: Int,
) : CrewEventDto

@Serializable
@SerialName("RIDE_CANCELLED")
public data class RideCancelledEventDto(
    override val id: String,
    override val crewId: String,
    override val occurredAt: Instant,
    override val actor: PersonDto,
    val rideId: String,
    val rideVersion: Int,
) : CrewEventDto

@Serializable
@SerialName("MEMBER_JOINED")
public data class MemberJoinedEventDto(
    override val id: String,
    override val crewId: String,
    override val occurredAt: Instant,
    override val actor: PersonDto,
    val member: CrewMemberDto,
) : CrewEventDto

/**
 * An event of a type added after this client was built. Only the common fields are kept: the UI skips it and the feed
 * cursor still advances past it. Clients never send it.
 */
@Serializable
@SerialName("UNKNOWN")
public data class UnknownCrewEventDto(
    override val id: String,
    override val crewId: String,
    override val occurredAt: Instant,
    override val actor: PersonDto,
) : CrewEventDto

/**
 * `GET /v1/crews/{crewId}/feed`. [lastEventId] is absent when [items] is empty; when [hasMore] is true, call again
 * with `since` = [lastEventId].
 */
@Serializable
public data class CrewFeedDto(
    val items: List<CrewEventDto>,
    val hasMore: Boolean,
    val lastEventId: String? = null,
)
