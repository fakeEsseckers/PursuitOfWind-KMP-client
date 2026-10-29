package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Shared test data. Defaults match the "C" vectors in .claude/skills/wind-domain-rules/reference.md.

val Int.kn: WindSpeed get() = WindSpeed.knots(toDouble())

val Double.kn: WindSpeed get() = WindSpeed.knots(this)

val BERLIN: TimeZone = TimeZone.of("Europe/Berlin")

/** 80 kg intermediate rider (12–28 kn, gust spread up to 10 kn) with a 7 / 9 / 12 m² quiver. */
val RIDER: RiderProfile = RiderProfile(weightKg = 80.0, level = SkillLevel.INTERMEDIATE, quiverM2 = listOf(7.0, 9.0, 12.0))

/** Rideable 180°–300°, dangerous 60°–150°, two hours away. */
val SPOT: SpotConfig =
    SpotConfig(
        id = SpotId("north-beach"),
        name = "North Beach",
        timeZone = BERLIN,
        rideableSectors = listOf(Sector.of(180.0, 300.0)),
        dangerSectors = listOf(Sector.of(60.0, 150.0)),
        drive = 2.hours,
    )

val ALL_MODELS: List<ModelId> = listOf(ModelId.ECMWF, ModelId.ICON, ModelId.GFS)

class ForecastHourBuilder {
    var start: Instant = Instant.parse("2026-07-18T11:00:00Z")
    var mean: WindSpeed = 18.kn
    var gust: WindSpeed = 24.kn
    var bearing: Double = 240.0
    var models: List<ModelId> = ALL_MODELS

    /** Overrides [mean], [gust], [bearing] and [models] when set. */
    var byModel: Map<ModelId, ModelWind>? = null
    var precipMm: Double = 0.0
    var tempC: Double = 15.0
    var thunderRisk: ThunderRisk = ThunderRisk.LOW
    var isDaylight: Boolean = true

    /** Mean and gust of every model in one statement: `forecastHour { wind(18.kn, 24.kn) }`. */
    fun wind(
        mean: WindSpeed,
        gust: WindSpeed,
    ) {
        this.mean = mean
        this.gust = gust
    }

    fun build(): ForecastHour =
        ForecastHour(
            start = start,
            byModel = byModel ?: models.associateWith { ModelWind(mean = mean, gust = gust, bearing = Bearing(bearing)) },
            precipMm = precipMm,
            tempC = tempC,
            thunderRisk = thunderRisk,
            isDaylight = isDaylight,
        )
}

/** `forecastHour { wind(18.kn, 24.kn) }`: three agreeing models, bearing 240°, daylight, dry, 15 °C. */
fun forecastHour(block: ForecastHourBuilder.() -> Unit = {}): ForecastHour = ForecastHourBuilder().apply(block).build()

/**
 * One forecast hour per letter, an hour apart from [firstStart]: `G` stays GO, `M` adds rain (MARGINAL),
 * `N` adds a thunderstorm (NO_GO). Spaces are ignored. [base] sets up every hour before the letter is applied.
 */
fun hoursFromPattern(
    firstStart: Instant,
    pattern: String,
    base: ForecastHourBuilder.() -> Unit = {},
): List<ForecastHour> =
    pattern.filterNot { it == ' ' }.mapIndexed { index, letter ->
        forecastHour {
            base()
            start = firstStart + index.hours
            when (letter) {
                'G' -> Unit
                'M' -> precipMm = 3.0
                'N' -> thunderRisk = ThunderRisk.HIGH
                else -> error("Unknown verdict letter '$letter'")
            }
        }
    }

/** The start of the hour at [localTime] on the fixtures' Saturday, 2026-07-18, in Berlin (UTC+2 in summer). */
fun berlin(localTime: String): Instant = LocalDateTime.parse("2026-07-18T$localTime").toInstant(BERLIN)

/** Smallest angle between two bearings, so 359.9999° and 0° are 0.0001° apart. */
fun angularDistance(
    a: Bearing,
    b: Bearing,
): Double {
    val difference = abs(a.degrees - b.degrees)
    return min(difference, 360.0 - difference)
}
