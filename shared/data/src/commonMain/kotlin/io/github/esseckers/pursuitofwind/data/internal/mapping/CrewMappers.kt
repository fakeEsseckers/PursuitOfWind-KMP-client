package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.CrewDto
import io.github.esseckers.pursuitofwind.contract.CrewMemberDto
import io.github.esseckers.pursuitofwind.data.internal.local.CrewEntity
import io.github.esseckers.pursuitofwind.data.internal.local.MemberEntity
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.CrewMember
import kotlin.time.Instant

// DTO → cache entity → domain. DTOs never leave the data layer.

internal fun CrewDto.toEntity(): CrewEntity = CrewEntity(id, name, inviteCode, memberCount, createdAt.toEpochMilliseconds())

internal fun CrewEntity.toCrew(): Crew = Crew(id, name, inviteCode, memberCount, Instant.fromEpochMilliseconds(createdAt))

internal fun CrewMemberDto.toEntity(crewId: String): MemberEntity =
    MemberEntity(crewId, userId, displayName, role.name, joinedAt.toEpochMilliseconds())

internal fun MemberEntity.toMember(): CrewMember =
    CrewMember(userId, displayName, cachedMemberRole(role), Instant.fromEpochMilliseconds(joinedAt))
