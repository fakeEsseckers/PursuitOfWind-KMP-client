package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.GeoPointDto
import io.github.esseckers.pursuitofwind.data.fake.ForecastProfile
import io.github.esseckers.pursuitofwind.data.fake.LIVE_DRIFT_DEG
import io.github.esseckers.pursuitofwind.data.fake.LIVE_DRIFT_KN
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * The weather of a spot a rider added, worked out from where it is, since nobody wrote a profile for it as for the
 * seeded spots. The same place and seed always give the same profile; the wind comes from any direction, so a spot's
 * sectors decide whether it works there, and it is warmer nearer the equator. Runs drift as on the seeded spots.
 */
internal object PlaceForecasts {
    private val MEAN_KN = 10.0..16.0
    private val SWING_KN = 4.0..7.0
    private val SWING_PERIOD_HOURS = 84..120
    private val SEA_BREEZE_KN = 1.5..3.0
    private val BEARING_SWING_DEG = 35.0..60.0
    private val GUST_FACTOR = 0.3..0.4
    private val MODEL_SPREAD_KN = 2.0..3.0
    private val NOISE_KN = 1.5..2.0
    private val RAINY_SHARE = 0.12..0.2
    private const val FULL_TURN_DEG = 360.0
    private const val EQUATOR_C = 28.0
    private const val COOLING_PER_DEGREE_C = 0.3
    private const val COLDEST_C = 4.0

    /** Steps per degree of the grid places snap to. */
    private const val PLACE_GRID = 10_000.0

    /** Above the 3,600,001 steps of longitude, so two places never share a key. */
    private const val HASH_MULTIPLIER = 4_000_037L

    fun profile(
        location: GeoPointDto,
        seed: Long,
    ): ForecastProfile {
        val latitude = location.lat.onGrid()
        val random = Random(seed xor (latitude * HASH_MULTIPLIER + location.lon.onGrid()))
        return ForecastProfile(
            meanKn = MEAN_KN.pick(random),
            swingKn = SWING_KN.pick(random),
            swingPeriodHours = random.nextInt(SWING_PERIOD_HOURS.first, SWING_PERIOD_HOURS.last + 1),
            seaBreezeKn = SEA_BREEZE_KN.pick(random),
            bearingDeg = random.nextDouble() * FULL_TURN_DEG,
            bearingSwingDeg = BEARING_SWING_DEG.pick(random),
            gustFactor = GUST_FACTOR.pick(random),
            modelSpreadKn = MODEL_SPREAD_KN.pick(random),
            noiseKn = NOISE_KN.pick(random),
            rainyShare = RAINY_SHARE.pick(random),
            temperatureC = (EQUATOR_C - abs(latitude) / PLACE_GRID * COOLING_PER_DEGREE_C).coerceAtLeast(COLDEST_C),
            runDriftKn = LIVE_DRIFT_KN,
            runDriftDeg = LIVE_DRIFT_DEG,
        )
    }

    /** This coordinate on a grid of about 10 m, so places closer than that are one place. */
    private fun Double.onGrid(): Long = (this * PLACE_GRID).roundToLong()

    private fun ClosedFloatingPointRange<Double>.pick(random: Random): Double = start + random.nextDouble() * (endInclusive - start)
}
