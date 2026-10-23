package io.github.esseckers.pursuitofwind.feature.today

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Person
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RideStatus
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
import io.github.esseckers.pursuitofwind.feature.today.domain.model.CrewRides
import io.github.esseckers.pursuitofwind.feature.today.domain.model.TodaySnapshot
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

val BERLIN: TimeZone = TimeZone.of("Europe/Berlin")
val THURSDAY: LocalDate = LocalDate(2026, 10, 22)
val FRIDAY: LocalDate = THURSDAY.plus(DatePeriod(days = 1))
val SATURDAY: LocalDate = THURSDAY.plus(DatePeriod(days = 2))
val SUNDAY: LocalDate = THURSDAY.plus(DatePeriod(days = 3))

/** 08:00 in Berlin, which is on summer time (UTC+2) until 25 October. */
val THURSDAY_8AM: Instant = at(THURSDAY, 8)

fun at(
    date: LocalDate,
    hour: Int,
    minute: Int = 0,
): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(BERLIN)

/** The sample rider: 80 kg, intermediate, kites 7 / 9 / 12 m². */
fun alex(unit: WindUnit = WindUnit.KNOTS): Profile =
    Profile(displayName = "Alex", rider = RiderProfile(80.0, SkillLevel.INTERMEDIATE, listOf(7.0, 9.0, 12.0)), windUnit = unit)

/** A spot, in Berlin's time zone unless [zone] says otherwise, that works with wind from 180° to 300°. */
fun mySpot(
    id: String,
    name: String = id,
    favorite: Boolean = true,
    zone: TimeZone = BERLIN,
): MySpot =
    MySpot(
        spot =
            Spot(
                id = SpotId(id),
                name = name,
                location = GeoPoint(54.5, 8.8),
                timeZone = zone,
                rideableSectors = listOf(Sector.of(180.0, 300.0)),
                dangerSectors = emptyList(),
                verified = true,
            ),
        drive = 2.hours,
        favorite = favorite,
    )

/**
 * One forecast hour from [hour]:00 local time on [date]. Two models agree on [meanKn] from [bearingDeg]. Daylight from
 * 08:00 to 17:00, as at the North Sea in late October.
 */
fun hour(
    date: LocalDate,
    hour: Int,
    meanKn: Double,
    gustKn: Double = meanKn + 4.0,
    bearingDeg: Double = 240.0,
): ForecastHour {
    val wind = ModelWind(WindSpeed.knots(meanKn), WindSpeed.knots(gustKn), Bearing(bearingDeg))
    return ForecastHour(
        start = at(date, hour),
        byModel = mapOf(ModelId.ECMWF to wind, ModelId.ICON to wind),
        precipMm = 0.0,
        tempC = 18.0,
        thunderRisk = ThunderRisk.LOW,
        isDaylight = hour in 8..16,
    )
}

/** All 24 hours of [date], with the mean wind of each hour from [meanKn]. */
fun day(
    date: LocalDate,
    bearingDeg: Double = 240.0,
    meanKn: (hour: Int) -> Double,
): List<ForecastHour> = (0..23).map { hour(date, it, meanKn(it), bearingDeg = bearingDeg) }

/** Light wind on Thursday and Friday; Saturday 13:00–17:00 at 18–22 kn. */
fun saturdayOn(): List<ForecastHour> =
    day(THURSDAY) { 6.0 } +
        day(FRIDAY) { 6.0 } +
        day(SATURDAY) { hour ->
            when (hour) {
                13 -> 18.0
                14 -> 20.0
                15 -> 22.0
                16 -> 20.0
                else -> 6.0
            }
        }

/** Andrei's ride to Nordstrand Bay for the 13:00–17:00 window on [date], with three free seats. */
fun ride(
    id: String,
    date: LocalDate = SATURDAY,
    departHour: Int = 9,
): Ride =
    Ride(
        id = id,
        version = 1,
        crewId = "crw_weekend",
        spotId = SpotId("nordstrand-bay"),
        driver = Person("usr_andrei", "Andrei"),
        windowStart = at(date, 13),
        windowEnd = at(date, 17),
        departAt = at(date, departHour),
        meetingPoint = "Hauptbahnhof",
        seatsTotal = 3,
        seatsFree = 3,
        passengers = emptyList(),
        status = RideStatus.SCHEDULED,
        createdAt = at(THURSDAY, 7),
    )

fun Ride.withPassengers(vararg people: Person): Ride = copy(passengers = people.toList(), seatsFree = seatsTotal - people.size)

/** Alex with Nordstrand Bay as the only favorite and [saturdayOn] cached, all fetched at [fetched]; no crew. */
internal fun todaySnapshot(
    fetched: Instant = THURSDAY_8AM - 20.minutes,
    profile: Cached<Profile?> = Cached(alex(), fetched),
    spots: Cached<List<MySpot>> = Cached(listOf(mySpot("nordstrand-bay")), fetched),
    forecasts: Map<SpotId, Cached<List<ForecastHour>>> = mapOf(SpotId("nordstrand-bay") to Cached(saturdayOn(), fetched)),
): TodaySnapshot = TodaySnapshot(profile, spots, forecasts, CrewRides(emptyList(), hasCrew = false, userId = null))
