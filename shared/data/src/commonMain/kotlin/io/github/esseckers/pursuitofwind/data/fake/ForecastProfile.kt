package io.github.esseckers.pursuitofwind.data.fake

/**
 * Parameters the fake server turns into hourly forecasts for one spot (all speeds in knots). The wind is a multi-day
 * weather swing plus an afternoon sea breeze plus seeded noise; the three models disagree by about [modelSpreadKn].
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
) {
    init {
        require(meanKn >= 0.0 && swingKn >= 0.0 && seaBreezeKn >= 0.0) { "Wind parameters must not be negative" }
        require(swingPeriodHours > 0) { "Swing period must be positive" }
        require(gustFactor >= 0.0 && modelSpreadKn >= 0.0 && noiseKn >= 0.0) { "Spread parameters must not be negative" }
        require(rainyShare in 0.0..1.0) { "Rainy share is a fraction, was $rainyShare" }
    }
}
