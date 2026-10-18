package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.internal.hourVerdict
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig

/**
 * Entry points of the decision engine. Every function is pure: same input, same output, no clock, no I/O.
 * The rules are documented in `docs/domain-rules.md`.
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
}
