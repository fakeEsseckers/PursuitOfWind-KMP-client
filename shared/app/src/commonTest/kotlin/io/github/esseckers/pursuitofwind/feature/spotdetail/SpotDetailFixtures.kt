package io.github.esseckers.pursuitofwind.feature.spotdetail

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.model.SpotDetailSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

val BERLIN: TimeZone = TimeZone.of("Europe/Berlin")
val LONDON: TimeZone = TimeZone.of("Europe/London")
val NORDSTRAND = SpotId("nordstrand-bay")

/** Berlin is on summer time (UTC+2) until Sunday 25 October 2026, 03:00, when clocks go back to 02:00. */
val FRIDAY: LocalDate = LocalDate(2026, 10, 23)
val SATURDAY: LocalDate = LocalDate(2026, 10, 24)
val SUNDAY: LocalDate = LocalDate(2026, 10, 25)
val MONDAY: LocalDate = LocalDate(2026, 10, 26)

fun at(
    date: LocalDate,
    hour: Int,
    minute: Int = 0,
): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(BERLIN)

/** The demo persona: 80 kg, intermediate, kites 7 / 9 / 12 m². */
fun alex(unit: WindUnit = WindUnit.KNOTS): Profile =
    Profile(displayName = "Alex", rider = RiderProfile(80.0, SkillLevel.INTERMEDIATE, listOf(7.0, 9.0, 12.0)), windUnit = unit)

/** Nordstrand Bay, two hours' drive away, rideable with wind from 180° to 300°. */
fun nordstrand(verified: Boolean = true): MySpot =
    MySpot(
        spot = Spot(NORDSTRAND, "Nordstrand Bay", GeoPoint(54.5, 8.8), BERLIN, listOf(Sector.of(180.0, 300.0)), emptyList(), verified),
        drive = 2.hours,
        favorite = true,
    )

/** Saturday 00:00 to Tuesday 00:00 in Berlin: three days, one of them 25 hours long. */
const val THREE_DAYS_OF_HOURS = 73

/**
 * [count] forecast hours from [from], one per real hour, so the repeated hour of a clock change appears twice. The
 * wind of each hour comes from [meanKn], given the local time at the spot. ECMWF and ICON agree; GFS, when
 * [withGfs], blows 4 kn more. Daylight from 08:00 to 17:00.
 */
fun forecast(
    from: Instant = at(SATURDAY, 0),
    count: Int = THREE_DAYS_OF_HOURS,
    bearingDeg: Double = 240.0,
    withGfs: Boolean = false,
    meanKn: (LocalDateTime) -> Double,
): List<ForecastHour> =
    (0 until count).map { i ->
        val start = from + i.hours
        val local = start.toLocalDateTime(BERLIN)
        val kn = meanKn(local)
        val wind = { offset: Double -> ModelWind(WindSpeed.knots(kn + offset), WindSpeed.knots(kn + offset + 4.0), Bearing(bearingDeg)) }
        val models =
            mapOf(ModelId.ECMWF to wind(0.0), ModelId.ICON to wind(0.0)) + if (withGfs) mapOf(ModelId.GFS to wind(4.0)) else emptyMap()
        ForecastHour(start, models, precipMm = 0.0, tempC = 14.0, thunderRisk = ThunderRisk.LOW, isDaylight = local.hour in 8..16)
    }

/** Light wind, apart from Saturday 13:00–17:00 at 18–22 kn. */
fun saturdayOn(withGfs: Boolean = false): List<ForecastHour> =
    forecast(withGfs = withGfs) { local ->
        if (local.date == SATURDAY) {
            when (local.hour) {
                13 -> 18.0
                14 -> 20.0
                15 -> 22.0
                16 -> 20.0
                else -> 6.0
            }
        } else {
            6.0
        }
    }

/** Alex, Nordstrand Bay and [hours], all fetched at [fetched]. */
internal fun snapshot(
    fetched: Instant = at(SATURDAY, 8) - 5.minutes,
    hours: List<ForecastHour> = saturdayOn(),
    spot: MySpot? = nordstrand(),
    profile: Profile? = alex(),
): SpotDetailSnapshot = SpotDetailSnapshot(Cached(profile, fetched), Cached(spot, fetched), Cached(hours, fetched))
