package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed

/** Rideable mean wind for a rider and the gust spread (gust − mean) they tolerate. */
internal data class WindRange(
    val min: WindSpeed,
    val max: WindSpeed,
    val gustTolerance: WindSpeed,
)

/**
 * Level defaults with the rider's personal limits applied. Personal limits replace the defaults in both directions,
 * so an advanced rider may set a minimum above the level's default maximum. Gust tolerance always comes from the level.
 */
internal fun RiderProfile.windRange(): WindRange {
    val defaults = level.defaultWindRange()
    return defaults.copy(min = minWind ?: defaults.min, max = maxWind ?: defaults.max)
}

internal fun SkillLevel.defaultWindRange(): WindRange =
    when (this) {
        SkillLevel.BEGINNER -> windRangeInKnots(minKn = 12.0, maxKn = 22.0, gustToleranceKn = 8.0)
        SkillLevel.INTERMEDIATE -> windRangeInKnots(minKn = 12.0, maxKn = 28.0, gustToleranceKn = 10.0)
        SkillLevel.ADVANCED -> windRangeInKnots(minKn = 10.0, maxKn = 35.0, gustToleranceKn = 14.0)
    }

private fun windRangeInKnots(
    minKn: Double,
    maxKn: Double,
    gustToleranceKn: Double,
): WindRange =
    WindRange(
        min = WindSpeed.knots(minKn),
        max = WindSpeed.knots(maxKn),
        gustTolerance = WindSpeed.knots(gustToleranceKn),
    )
