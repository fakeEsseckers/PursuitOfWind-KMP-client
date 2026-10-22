package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.contract.SkillLevelDto
import io.github.esseckers.pursuitofwind.contract.WindUnitDto
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.time.Duration.Companion.minutes

// DTO ↔ domain. DTOs never leave the data layer.

internal fun RiderProfileDto.toProfile(): Profile =
    Profile(
        displayName = displayName,
        rider =
            RiderProfile(
                weightKg = weightKg,
                level = SkillLevel.valueOf(level.name),
                quiverM2 = quiverM2,
                minWind = minWindMps?.let(::WindSpeed),
                maxWind = maxWindMps?.let(::WindSpeed),
                minSession = minSessionMinutes.minutes,
                maxDrive = maxDriveMinutes.minutes,
            ),
        windUnit = WindUnit.valueOf(windUnit.name),
    )

internal fun Profile.toDto(): RiderProfileDto =
    RiderProfileDto(
        displayName = displayName,
        weightKg = rider.weightKg,
        level = SkillLevelDto.valueOf(rider.level.name),
        quiverM2 = rider.quiverM2,
        minWindMps = rider.minWind?.metersPerSecond,
        maxWindMps = rider.maxWind?.metersPerSecond,
        minSessionMinutes = rider.minSession.inWholeMinutes.toInt(),
        maxDriveMinutes = rider.maxDrive.inWholeMinutes.toInt(),
        windUnit = WindUnitDto.valueOf(windUnit.name),
    )
