package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A user as other crew members see them. */
@Serializable
public data class PersonDto(
    val userId: String,
    val displayName: String,
)

/** `POST /v1/crews`. */
@Serializable
public data class CreateCrewRequestDto(
    val name: String,
)

/** `POST /v1/crews/join`. */
@Serializable
public data class JoinCrewRequestDto(
    val inviteCode: String,
)

@Serializable
public data class CrewDto(
    val id: String,
    val name: String,
    val inviteCode: String,
    val memberCount: Int,
    val createdAt: Instant,
)

/** `GET /v1/me/crews`. */
@Serializable
public data class CrewListDto(
    val items: List<CrewDto>,
)

@Serializable
public enum class MemberRoleDto {
    OWNER,
    MEMBER,
}

@Serializable
public data class CrewMemberDto(
    val userId: String,
    val displayName: String,
    val role: MemberRoleDto,
    val joinedAt: Instant,
)

/** `GET /v1/crews/{crewId}/members`. */
@Serializable
public data class CrewMemberListDto(
    val items: List<CrewMemberDto>,
)
