package io.github.esseckers.pursuitofwind.engine.model

import kotlin.jvm.JvmInline

/**
 * Wind speed. Stored in metres per second (SI); converted only at the edges (UI, test fixtures).
 */
@JvmInline
public value class WindSpeed(
    public val metersPerSecond: Double,
) : Comparable<WindSpeed> {
    init {
        require(metersPerSecond.isFinite() && metersPerSecond >= 0.0) {
            "Wind speed must be a finite, non-negative number of m/s, was $metersPerSecond"
        }
    }

    public fun toKnots(): Double = metersPerSecond / METERS_PER_SECOND_PER_KNOT

    public fun toKmh(): Double = metersPerSecond * KMH_PER_METER_PER_SECOND

    /** Beaufort force 0–12. Upper bounds per force are exclusive. */
    public fun toBeaufort(): Int {
        val force = BEAUFORT_UPPER_BOUNDS_MS.indexOfFirst { metersPerSecond < it }
        return if (force == -1) BEAUFORT_UPPER_BOUNDS_MS.size else force
    }

    override fun compareTo(other: WindSpeed): Int = metersPerSecond.compareTo(other.metersPerSecond)

    override fun toString(): String = "$metersPerSecond m/s"

    public companion object {
        public fun knots(value: Double): WindSpeed = WindSpeed(value * METERS_PER_SECOND_PER_KNOT)

        public fun kmh(value: Double): WindSpeed = WindSpeed(value / KMH_PER_METER_PER_SECOND)
    }
}

/** One knot is one nautical mile (1852 m) per hour. */
private const val METERS_PER_SECOND_PER_KNOT: Double = 1852.0 / 3600.0

private const val KMH_PER_METER_PER_SECOND: Double = 3.6

/** Exclusive upper bound of Beaufort forces 0–11 in m/s. Anything at or above the last bound is force 12. */
private val BEAUFORT_UPPER_BOUNDS_MS: DoubleArray =
    doubleArrayOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)
