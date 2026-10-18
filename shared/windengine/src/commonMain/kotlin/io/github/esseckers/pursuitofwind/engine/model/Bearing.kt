package io.github.esseckers.pursuitofwind.engine.model

import kotlin.jvm.JvmInline

/**
 * Direction the wind blows **from**, in degrees clockwise from true north, normalized to [0, 360).
 *
 * Any finite angle is accepted and normalized: `Bearing(-10.0)` is 350°, `Bearing(370.0)` is 10°, `Bearing(360.0)` is 0°.
 */
@JvmInline
public value class Bearing private constructor(
    public val degrees: Double,
) {
    override fun toString(): String = "$degrees°"

    public companion object {
        public operator fun invoke(degrees: Double): Bearing {
            require(degrees.isFinite()) { "Bearing must be a finite number of degrees, was $degrees" }
            return Bearing(normalizeDegrees(degrees))
        }
    }
}

/** Degrees in a full turn. */
internal const val FULL_TURN_DEGREES: Double = 360.0

/** Maps any finite angle to [0, 360). */
internal fun normalizeDegrees(degrees: Double): Double {
    val remainder = degrees % FULL_TURN_DEGREES
    val positive = if (remainder < 0.0) remainder + FULL_TURN_DEGREES else remainder
    // A tiny negative remainder plus 360 rounds to exactly 360.0, and -0.0 is a valid remainder: both mean north.
    return if (positive >= FULL_TURN_DEGREES || positive == 0.0) 0.0 else positive
}

/** Clockwise angle from [from] to [to], in [0, 360). */
internal fun clockwiseDegrees(
    from: Bearing,
    to: Bearing,
): Double = normalizeDegrees(to.degrees - from.degrees)
