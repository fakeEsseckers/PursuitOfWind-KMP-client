package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.SkillLevelDto
import io.github.esseckers.pursuitofwind.contract.ThunderRiskDto
import io.github.esseckers.pursuitofwind.contract.WindUnitDto
import io.github.esseckers.pursuitofwind.data.model.MemberRole
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk

// Enums between the API and the domain. Each mapping is an exhaustive `when`, so a value added on one side does not
// compile until it is mapped on the other. Cached names are read in CachedEnums.kt.

internal fun SkillLevelDto.toModel(): SkillLevel =
    when (this) {
        SkillLevelDto.BEGINNER -> SkillLevel.BEGINNER
        SkillLevelDto.INTERMEDIATE -> SkillLevel.INTERMEDIATE
        SkillLevelDto.ADVANCED -> SkillLevel.ADVANCED
    }

internal fun SkillLevel.toDto(): SkillLevelDto =
    when (this) {
        SkillLevel.BEGINNER -> SkillLevelDto.BEGINNER
        SkillLevel.INTERMEDIATE -> SkillLevelDto.INTERMEDIATE
        SkillLevel.ADVANCED -> SkillLevelDto.ADVANCED
    }

internal fun WindUnitDto.toModel(): WindUnit =
    when (this) {
        WindUnitDto.KNOTS -> WindUnit.KNOTS
        WindUnitDto.METERS_PER_SECOND -> WindUnit.METERS_PER_SECOND
        WindUnitDto.KILOMETERS_PER_HOUR -> WindUnit.KILOMETERS_PER_HOUR
        WindUnitDto.BEAUFORT -> WindUnit.BEAUFORT
    }

internal fun WindUnit.toDto(): WindUnitDto =
    when (this) {
        WindUnit.KNOTS -> WindUnitDto.KNOTS
        WindUnit.METERS_PER_SECOND -> WindUnitDto.METERS_PER_SECOND
        WindUnit.KILOMETERS_PER_HOUR -> WindUnitDto.KILOMETERS_PER_HOUR
        WindUnit.BEAUFORT -> WindUnitDto.BEAUFORT
    }

internal fun MemberRoleDto.toModel(): MemberRole =
    when (this) {
        MemberRoleDto.OWNER -> MemberRole.OWNER
        MemberRoleDto.MEMBER -> MemberRole.MEMBER
    }

internal fun RideStatusDto.toModel(): RideStatus =
    when (this) {
        RideStatusDto.SCHEDULED -> RideStatus.SCHEDULED
        RideStatusDto.CANCELLED -> RideStatus.CANCELLED
    }

internal fun ThunderRiskDto.toModel(): ThunderRisk =
    when (this) {
        ThunderRiskDto.LOW -> ThunderRisk.LOW
        ThunderRiskDto.MEDIUM -> ThunderRisk.MEDIUM
        ThunderRiskDto.HIGH -> ThunderRisk.HIGH
    }
