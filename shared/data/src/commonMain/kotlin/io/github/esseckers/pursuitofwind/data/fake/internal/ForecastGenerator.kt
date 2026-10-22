package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ForecastHourDto
import io.github.esseckers.pursuitofwind.contract.ModelWindDto
import io.github.esseckers.pursuitofwind.contract.ThunderRiskDto
import io.github.esseckers.pursuitofwind.data.fake.ForecastProfile
import io.github.esseckers.pursuitofwind.data.fake.SeedSpot
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Procedural forecasts for three models. Every value is a pure function of (seed, spot, hour, model), so the same hour
 * looks the same whatever `from` a request uses, on every run. Across platforms values can differ in rare cases:
 * `sin` and `acos` may differ in the last bit between the JVM and native math libraries, which can flip a rounding.
 */
internal object ForecastGenerator {
    val MODELS = listOf("ecmwf", "icon", "gfs")

    private const val SECONDS_PER_HOUR = 3_600L
    private const val HOURS_PER_DAY = 24
    private const val DEGREES_PER_HOUR = 15.0
    private const val SEA_BREEZE_START = 10.0
    private const val SEA_BREEZE_HOURS = 9.0
    private const val DIRECTION_NOISE_DEG = 6.0
    private const val MODEL_DIRECTION_OFFSET_DEG = 4.0
    private const val BEARING_SWING_PERIOD_FACTOR = 0.7
    private const val TEMPERATURE_RISE_C = 5.0
    private const val RAIN_MIN_MM = 0.3
    private const val RAIN_RANGE_MM = 3.5
    private const val HEAVY_RAIN_MM = 3.0
    private const val SHOWERY_RAIN_MM = 2.0
    private const val STORMY_SWING = 0.6
    private const val FULL_TURN = 360.0
    private const val WEATHER_MODEL = 9

    fun hours(
        spot: SeedSpot,
        from: Instant,
        count: Int,
        seed: Long,
    ): List<ForecastHourDto> {
        val firstHour = from.epochSeconds.floorDiv(SECONDS_PER_HOUR)
        return (0 until count).map { offset -> hour(spot, firstHour + offset, Noise(seed, stableHash(spot.spot.id))) }
    }

    private fun hour(
        spot: SeedSpot,
        hourIndex: Long,
        noise: Noise,
    ): ForecastHourDto {
        val profile = spot.forecast
        val start = Instant.fromEpochSeconds(hourIndex * SECONDS_PER_HOUR)
        val phase = noise.phase(profile.swingPeriodHours)
        val swing = sin(2 * PI * (hourIndex + phase) / profile.swingPeriodHours)
        val seaBreeze = seaBreeze(hourIndex, spot.spot.location.lon)
        val baseKn = profile.meanKn + profile.swingKn * swing + profile.seaBreezeKn * seaBreeze
        val models = MODELS.mapIndexed { index, model -> modelWind(model, index, baseKn, hourIndex, phase, profile, noise) }

        val rainRoll = (noise.at(hourIndex, WEATHER_MODEL, salt = 5) + 1) / 2
        val precipitation = if (rainRoll < profile.rainyShare) RAIN_MIN_MM + rainRoll / profile.rainyShare * RAIN_RANGE_MM else 0.0
        return ForecastHourDto(
            start = start,
            models = models,
            precipitationMm = precipitation.round2(),
            temperatureC = (profile.temperatureC + TEMPERATURE_RISE_C * seaBreeze + noise.at(hourIndex, WEATHER_MODEL, salt = 6)).round2(),
            thunderRisk =
                when {
                    precipitation > HEAVY_RAIN_MM && swing > STORMY_SWING -> ThunderRiskDto.HIGH
                    precipitation > SHOWERY_RAIN_MM -> ThunderRiskDto.MEDIUM
                    else -> ThunderRiskDto.LOW
                },
            isDaylight = SunTimes.isDaylight(spot.spot.location.lat, spot.spot.location.lon, start),
        )
    }

    @Suppress("LongParameterList") // One call site; a parameter object would only rename the same seven values.
    private fun modelWind(
        model: String,
        index: Int,
        baseKn: Double,
        hourIndex: Long,
        phase: Double,
        profile: ForecastProfile,
        noise: Noise,
    ): ModelWindDto {
        val offsetKn =
            (index - 1) * profile.modelSpreadKn / 2 + noise.at(hourIndex, index, salt = 1) * profile.modelSpreadKn / MODEL_JITTER_DIVISOR
        val meanKn = max(0.0, baseKn + offsetKn + noise.at(hourIndex, index, salt = 2) * profile.noiseKn)
        val gustKn = meanKn * (1 + profile.gustFactor) + abs(noise.at(hourIndex, index, salt = 3)) * profile.noiseKn
        val bearing =
            profile.bearingDeg +
                profile.bearingSwingDeg * sin(2 * PI * (hourIndex + 2 * phase) / (profile.swingPeriodHours * BEARING_SWING_PERIOD_FACTOR)) +
                noise.at(hourIndex, index, salt = 4) * DIRECTION_NOISE_DEG +
                (index - 1) * MODEL_DIRECTION_OFFSET_DEG
        return ModelWindDto(
            model = model,
            meanMps = WindSpeed.knots(meanKn).metersPerSecond.round2(),
            gustMps = WindSpeed.knots(gustKn).metersPerSecond.round2(),
            bearingDeg = bearing.mod(FULL_TURN).round2().let { if (it >= FULL_TURN) 0.0 else it },
        )
    }

    /** 0 outside 10:00–19:00 local solar time, rising to 1 in mid-afternoon. */
    private fun seaBreeze(
        hourIndex: Long,
        longitude: Double,
    ): Double {
        val localSolarHour = ((hourIndex % HOURS_PER_DAY) + longitude / DEGREES_PER_HOUR + HOURS_PER_DAY) % HOURS_PER_DAY
        val sinceStart = localSolarHour - SEA_BREEZE_START
        return if (sinceStart in 0.0..SEA_BREEZE_HOURS) sin(PI * sinceStart / SEA_BREEZE_HOURS) else 0.0
    }

    /** Same result on every platform, unlike `String.hashCode` guarantees. */
    private fun stableHash(text: String): Long = text.fold(HASH_SEED) { hash, char -> hash * HASH_MULTIPLIER + char.code }

    private fun Double.round2(): Double = round(this * HUNDREDTHS) / HUNDREDTHS

    /** Models sit half the spread apart; on top of that each wobbles by up to a third of it. */
    private const val MODEL_JITTER_DIVISOR = 3.0
    private const val HUNDREDTHS = 100.0
    private const val HASH_SEED = 1_125_899_906_842_597L
    private const val HASH_MULTIPLIER = 31L
}

/** Deterministic noise in [-1, 1] from SplitMix64, keyed by seed, spot, hour, model and a salt per quantity. */
private class Noise(
    private val seed: Long,
    private val spotHash: Long,
) {
    fun at(
        hourIndex: Long,
        model: Int,
        salt: Int,
    ): Double {
        val key =
            seed xor spotHash.mix() xor (hourIndex * HOUR_STRIDE) xor (model.toLong() shl MODEL_SHIFT) xor (salt.toLong() shl SALT_SHIFT)
        val unit = (key.mix() ushr UNIT_SHIFT).toDouble() / UNIT_RANGE
        return unit * 2 - 1
    }

    fun phase(period: Int): Double = ((spotHash.mix() ushr 1) % period).toDouble()

    /** The SplitMix64 finalizer; the shift amounts are part of the published algorithm. */
    @Suppress("MagicNumber")
    private fun Long.mix(): Long {
        var z = (this.toULong() + GOLDEN_GAMMA)
        z = (z xor (z shr 30)) * MIX_1
        z = (z xor (z shr 27)) * MIX_2
        return (z xor (z shr 31)).toLong()
    }

    private companion object {
        const val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL
        const val MIX_1 = 0xBF58476D1CE4E5B9uL
        const val MIX_2 = 0x94D049BB133111EBuL
        const val HOUR_STRIDE = 0x2545F4914F6CDD1DL
        const val MODEL_SHIFT = 48
        const val SALT_SHIFT = 56
        const val UNIT_SHIFT = 11

        /** 2^53: the doubles in [0, 1) with full precision. */
        const val UNIT_RANGE = 9_007_199_254_740_992.0
    }
}
