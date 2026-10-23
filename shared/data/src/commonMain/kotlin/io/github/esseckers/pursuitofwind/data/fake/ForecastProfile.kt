package io.github.esseckers.pursuitofwind.data.fake

/**
 * Parameters the fake server turns into hourly forecasts for one spot (all speeds in knots). The wind is a multi-day
 * weather swing plus an afternoon sea breeze plus seeded noise; the three models disagree by about [modelSpreadKn].
 *
 * Like a live server, every forecast request is a new model run, and runs disagree a little:
 * @property runDriftKn how far a new run can move the mean wind, three days out; the next hours move a quarter of it.
 *   Zero makes every run identical.
 * @property runDriftDeg the same for the wind direction, in degrees.
 */
public data class ForecastProfile(
    val meanKn: Double,
    val swingKn: Double,
    val swingPeriodHours: Int,
    val seaBreezeKn: Double,
    val bearingDeg: Double,
    val bearingSwingDeg: Double,
    val gustFactor: Double,
    val modelSpreadKn: Double,
    val noiseKn: Double,
    val rainyShare: Double,
    val temperatureC: Double,
    val runDriftKn: Double = 0.0,
    val runDriftDeg: Double = 0.0,
) {
    init {
        require(meanKn >= 0.0 && swingKn >= 0.0 && seaBreezeKn >= 0.0) { "Wind parameters must not be negative" }
        require(swingPeriodHours > 0) { "Swing period must be positive" }
        require(gustFactor >= 0.0 && modelSpreadKn >= 0.0 && noiseKn >= 0.0) { "Spread parameters must not be negative" }
        require(rainyShare in 0.0..1.0) { "Rainy share is a fraction, was $rainyShare" }
        require(runDriftKn >= 0.0 && runDriftDeg >= 0.0) { "Run drift must not be negative" }
    }
}
