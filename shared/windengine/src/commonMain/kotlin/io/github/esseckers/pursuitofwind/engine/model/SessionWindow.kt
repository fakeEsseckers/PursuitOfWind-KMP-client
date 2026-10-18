package io.github.esseckers.pursuitofwind.engine.model

import kotlin.time.Duration
import kotlin.time.Instant

/** How much the weather models agree on the wind inside a window. */
public enum class Confidence(
    internal val scoreFactor: Double,
) {
    HIGH(scoreFactor = 1.0),
    MEDIUM(scoreFactor = 0.85),
    LOW(scoreFactor = 0.6),

    /** Only one model was available, so agreement cannot be judged. */
    UNKNOWN(scoreFactor = 0.8),
}

/**
 * A run of rideable hours at one spot: GO hours with isolated MARGINAL hours bridged.
 *
 * @property end exclusive: the end of the last hour.
 * @property hours number of forecast hours in the window, bridged MARGINAL hours included.
 * @property leaveBy latest departure to arrive and rig before [start].
 * @property usableStart when riding can start: [start], or later if it is already past [leaveBy].
 * @property usable riding time from [usableStart] to [end]; never negative.
 * @property score ranking value: usable hours × quality × confidence factor (see `docs/domain-rules.md`).
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
    public val score: Double,
    public val recommendedKiteM2: Double?,
    public val qualifies: Boolean,
)
