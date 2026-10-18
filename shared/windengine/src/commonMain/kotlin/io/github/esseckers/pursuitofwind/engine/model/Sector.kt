package io.github.esseckers.pursuitofwind.engine.model

/**
 * Clockwise arc of wind directions from [from] to [to], both ends included. The arc may wrap past north:
 * `Sector.of(300.0, 60.0)` covers north-west through north to north-east.
 *
 * Equal bounds are rejected: they would be ambiguous between "one direction" and "every direction".
 * Bounds match with a tolerance of 1e-9°, so a direction computed as 59.99999999999999° still counts as 60°,
 * and two sectors that share a bound both contain it.
 */
public data class Sector(
    public val from: Bearing,
    public val to: Bearing,
) {
    init {
        require(from != to) { "A sector needs two different bounds, got $from for both" }
    }

    /** Width of the arc in degrees, in (0, 360). */
    public val widthDegrees: Double
        get() = clockwiseDegrees(from, to)

    public operator fun contains(bearing: Bearing): Boolean {
        val offset = clockwiseDegrees(from, bearing)
        return offset <= widthDegrees + BOUND_TOLERANCE_DEGREES || offset >= FULL_TURN_DEGREES - BOUND_TOLERANCE_DEGREES
    }

    public companion object {
        /** `Sector.of(300.0, 60.0)`: shorthand with plain degrees, normalized like [Bearing]. */
        public fun of(
            fromDegrees: Double,
            toDegrees: Double,
        ): Sector = Sector(Bearing(fromDegrees), Bearing(toDegrees))
    }
}

/** Directions are averaged through sin, cos and atan2; this absorbs the last-bit differences between platforms. */
private const val BOUND_TOLERANCE_DEGREES: Double = 1e-9
