package io.github.esseckers.pursuitofwind.engine.model

import kotlin.time.Instant

/**
 * Consensus wind of all models for one hour.
 *
 * @property bearing vector mean of the models' directions.
 * @property modelSpread highest minus lowest model mean wind; null when only one model is available.
 */
public data class HourWind(
    public val mean: WindSpeed,
    public val gust: WindSpeed,
    public val bearing: Bearing,
    public val modelSpread: WindSpeed?,
)

/**
 * Verdict for one forecast hour at one spot for one rider.
 *
 * @property reasons every non-GO reason, most severe first. Empty for GO.
 * @property recommendedKiteM2 kite from the rider's quiver for this hour. Null when the quiver is empty or every kite is
 *   too big or too small; when the ideal size falls between two kites, the smaller one (with MARGINAL
 *   UNDERPOWERED_FOR_QUIVER). It is computed for NO_GO hours too; the UI decides whether to show it.
 */
public data class HourVerdict(
    public val start: Instant,
    public val verdict: Verdict,
    public val reasons: List<Reason>,
    public val recommendedKiteM2: Double?,
    public val wind: HourWind,
)
