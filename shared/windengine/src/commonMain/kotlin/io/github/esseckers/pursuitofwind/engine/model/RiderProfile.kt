package io.github.esseckers.pursuitofwind.engine.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** How experienced the rider is. Sets the default wind range and the tolerated gust spread. */
public enum class SkillLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

/**
 * The rider the decision is made for.
 *
 * @property weightKg body weight, drives kite sizing.
 * @property quiverM2 kite sizes the rider owns, in square metres. Order does not matter. Empty means "do not size kites".
 * @property minWind personal lower wind limit; replaces the level default when set.
 * @property maxWind personal upper wind limit; replaces the level default when set.
 * @property minSession shortest session worth the drive, counted after arrival and rigging.
 * @property maxDrive spots with a longer drive are never suggested.
 */
public data class RiderProfile(
    public val weightKg: Double,
    public val level: SkillLevel,
    public val quiverM2: List<Double>,
    public val minWind: WindSpeed? = null,
    public val maxWind: WindSpeed? = null,
    public val minSession: Duration = DEFAULT_MIN_SESSION,
    public val maxDrive: Duration = DEFAULT_MAX_DRIVE,
) {
    init {
        require(weightKg.isFinite() && weightKg > 0.0) { "Weight must be a positive number of kg, was $weightKg" }
        require(quiverM2.all { it.isFinite() && it > 0.0 }) { "Kite sizes must be positive numbers of m², were $quiverM2" }
        if (minWind != null && maxWind != null) {
            require(minWind < maxWind) { "Personal min wind $minWind must be below max wind $maxWind" }
        }
        require(!minSession.isNegative()) { "Minimum session must not be negative, was $minSession" }
        require(!maxDrive.isNegative()) { "Maximum drive must not be negative, was $maxDrive" }
    }

    public companion object {
        public val DEFAULT_MIN_SESSION: Duration = 2.hours
        public val DEFAULT_MAX_DRIVE: Duration = 3.hours
    }
}
