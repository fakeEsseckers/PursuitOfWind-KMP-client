package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.internal.dayDecisions
import io.github.esseckers.pursuitofwind.engine.internal.hourVerdict
import io.github.esseckers.pursuitofwind.engine.internal.shoreSectors
import io.github.esseckers.pursuitofwind.engine.internal.windowsAt
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import kotlin.time.Instant

/**
 * Entry points of the decision engine. Every function is pure: same input, same output, no clock, no I/O.
 * The current time is always a parameter. The rules are documented in `docs/domain-rules.md`.
 *
 * Verdicts, kite sizes and windows support a decision. They do not replace judging conditions on the spot
 * and are not safety advice.
 */
public object DecisionEngine {
    /** GO, MARGINAL or NO_GO for one forecast hour, with every reason and a recommended kite. */
    public fun evaluateHour(
        hour: ForecastHour,
        profile: RiderProfile,
        spot: SpotConfig,
    ): HourVerdict = hourVerdict(hour = hour, profile = profile, spot = spot)

    /**
     * Session windows in [hours] of one spot, in time order, including the ones that are too short to qualify.
     * Empty when the spot is beyond the rider's maximum drive.
     */
    public fun findWindows(
        hours: List<HourVerdict>,
        spot: SpotConfig,
        profile: RiderProfile,
        now: Instant,
    ): List<SessionWindow> = windowsAt(spot = spot, hours = hours, profile = profile, now = now)

    /**
     * One [DayDecision] per local date from today until the end of the forecast, across the rider's favorite spots.
     * Each spot may appear once in [forecasts].
     */
    public fun decideDays(
        forecasts: List<SpotForecast>,
        profile: RiderProfile,
        now: Instant,
    ): List<DayDecision> = dayDecisions(forecasts = forecasts, profile = profile, now = now)

    /**
     * The sectors of a spot whose open water lies towards [water], seen from the beach. Wind from within 100° of the
     * water is rideable, wind from within 60° of the land is dangerous, and the 20° between them on either side is
     * neither, so it counts as the wrong direction for the spot.
     */
    public fun sectorsFacing(water: Bearing): ShoreSectors = shoreSectors(water)
}
