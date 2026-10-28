package io.github.esseckers.pursuitofwind.engine.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * How much the weather models agree on the wind inside a window. Between two equally long windows, the one with more
 * [preference] ranks first: unknown agreement lies between MEDIUM and LOW.
 */
public enum class Confidence(
    internal val preference: Int,
) {
    HIGH(preference = 3),
    MEDIUM(preference = 2),
    LOW(preference = 0),

    /** Only one model was available, so agreement cannot be judged. */
    UNKNOWN(preference = 1),
}

/**
 * A run of rideable hours at one spot: GO hours with isolated MARGINAL hours bridged.
 *
 * @property end exclusive: the end of the last hour.
 * @property hours number of forecast hours in the window, bridged MARGINAL hours included.
 * @property leaveBy latest departure to arrive and rig before [start].
 * @property usableStart when riding can start: [start], or later if it is already past [leaveBy].
 * @property usable riding time from [usableStart] to [end]; never negative.
 * @property recommendedKiteM2 most frequent kite of the window's hours, the smaller one on a tie.
 * @property qualifies false when [usable] is shorter than the rider's minimum session (WINDOW_TOO_SHORT).
 */
public data class SessionWindow(
    public val spotId: SpotId,
    public val start: Instant,
    public val end: Instant,
    public val hours: Int,
    public val marginalHours: Int,
    public val leaveBy: Instant,
    public val usableStart: Instant,
    public val usable: Duration,
    public val confidence: Confidence,
    public val recommendedKiteM2: Double?,
    public val qualifies: Boolean,
) {
    /** True while the rider can still get there before the window ends: some [usable] riding time is left. */
    public val isReachable: Boolean get() = usable > Duration.ZERO

    /** True once it is time to leave at [now]: [leaveBy] has come. */
    public fun mustLeaveNow(now: Instant): Boolean = now >= leaveBy

    /** True when the forecast hour that starts at [hourStart] has riding time in it, between [usableStart] and [end]. */
    public fun hasRidingIn(hourStart: Instant): Boolean = hourStart < end && hourStart + 1.hours > usableStart
}
