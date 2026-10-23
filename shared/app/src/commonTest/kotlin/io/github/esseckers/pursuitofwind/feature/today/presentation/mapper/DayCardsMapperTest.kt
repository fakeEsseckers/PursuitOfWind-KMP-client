package io.github.esseckers.pursuitofwind.feature.today.presentation.mapper

import io.github.esseckers.pursuitofwind.data.model.Person
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.BERLIN
import io.github.esseckers.pursuitofwind.feature.today.FRIDAY
import io.github.esseckers.pursuitofwind.feature.today.SATURDAY
import io.github.esseckers.pursuitofwind.feature.today.SUNDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.alex
import io.github.esseckers.pursuitofwind.feature.today.at
import io.github.esseckers.pursuitofwind.feature.today.day
import io.github.esseckers.pursuitofwind.feature.today.domain.model.CrewRides
import io.github.esseckers.pursuitofwind.feature.today.hour
import io.github.esseckers.pursuitofwind.feature.today.mySpot
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.AlternativeUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayLabel
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.SpotReasonUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TimeRange
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.WindRange
import io.github.esseckers.pursuitofwind.feature.today.ride
import io.github.esseckers.pursuitofwind.feature.today.saturdayOn
import io.github.esseckers.pursuitofwind.feature.today.withPassengers
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DayCardsMapperTest {
    private val me = Person("usr_0001", "Alex")

    private fun cards(
        forecasts: Map<String, List<ForecastHour>> = mapOf("nordstrand-bay" to saturdayOn()),
        rides: CrewRides = CrewRides(emptyList(), hasCrew = false, userId = me.userId),
        unit: WindUnit = WindUnit.KNOTS,
        now: Instant = THURSDAY_8AM,
        riderZone: TimeZone = BERLIN,
    ): List<DayUi> =
        DayCardsMapper(
            profile = alex(unit),
            spots = forecasts.map { (id, hours) -> SpotWithForecast(mySpot(id, name = NAMES.getValue(id)), hours) },
            crew = rides,
            now = now,
            riderZone = riderZone,
        ).map()

    @Test
    fun `a GO day has the spot and the window in local time with wind and kite and when to leave`() {
        val saturday = assertIs<DayUi.Go>(cards()[2])

        assertEquals(DayLabel.Weekday(DayOfWeek.SATURDAY), saturday.label)
        assertEquals("Nordstrand Bay", saturday.spotName)
        assertEquals(TimeRange("13:00", "17:00"), saturday.window)
        assertEquals(WindRange(18, 22, WindUnit.KNOTS), saturday.wind)
        assertEquals(9.0, saturday.kiteM2)
        assertEquals("10:30", saturday.leaveBy)
        assertEquals(false, saturday.leaveNow)
        assertEquals(Confidence.HIGH, saturday.confidence)
    }

    @Test
    fun `a NO-GO day gives the main reason and the next GO day`() {
        val (today, tomorrow) = cards()

        assertEquals(
            DayUi.NoGo(THURSDAY, DayLabel.Today, Reason.TOO_LIGHT, spots = emptyList(), nextGoDay = DayLabel.Weekday(DayOfWeek.SATURDAY)),
            today,
        )
        assertEquals(DayLabel.Tomorrow, tomorrow.label)
    }

    @Test
    fun `the reason of each spot is listed when there is more than one spot`() {
        val offshore = day(THURSDAY, bearingDeg = 100.0) { 18.0 }
        val today = cards(forecasts = mapOf("nordstrand-bay" to day(THURSDAY) { 6.0 }, "salt-lagoon" to offshore)).first()

        assertEquals(
            listOf(SpotReasonUi("Nordstrand Bay", Reason.TOO_LIGHT), SpotReasonUi("Salt Lagoon", Reason.DIRECTION_UNRIDEABLE)),
            assertIs<DayUi.NoGo>(today).spots,
        )
    }

    @Test
    fun `the next best windows are offered as alternatives`() {
        val weaker = day(THURSDAY) { 6.0 } + day(FRIDAY) { 6.0 } + day(SATURDAY) { if (it in 14..15) 20.0 else 6.0 }
        val saturday = cards(forecasts = mapOf("nordstrand-bay" to saturdayOn(), "salt-lagoon" to weaker))[2]

        assertEquals(listOf(AlternativeUi("Salt Lagoon", TimeRange("14:00", "16:00"))), assertIs<DayUi.Go>(saturday).alternatives)
    }

    @Test
    fun `a GO day lists the rides of the crew to that spot on that day with the role of the rider`() {
        val rides =
            listOf(
                ride("andrei", departHour = 10).withPassengers(Person("usr_mira", "Mira")),
                ride("mine", departHour = 9).copy(driver = me),
                ride("full", departHour = 11).copy(seatsTotal = 1).withPassengers(me),
                ride("friday", date = FRIDAY),
                ride("elsewhere").copy(spotId = SpotId("salt-lagoon")),
                ride("cancelled").copy(status = RideStatus.CANCELLED),
            )
        val saturday = assertIs<DayUi.Go>(cards(rides = CrewRides(rides, hasCrew = true, userId = me.userId))[2])

        assertEquals(
            listOf(
                RideUi("mine", "Alex", "09:00", departed = false, seatsFree = 3, youDrive = true, youRide = false, pending = false),
                RideUi("andrei", "Andrei", "10:00", departed = false, seatsFree = 2, youDrive = false, youRide = false, pending = false),
                RideUi("full", "Andrei", "11:00", departed = false, seatsFree = 0, youDrive = false, youRide = true, pending = false),
            ),
            saturday.rides,
        )
        assertTrue(saturday.hasCrew)
    }

    @Test
    fun `wind is shown in the unit of the rider`() {
        fun saturdayWind(unit: WindUnit) = assertIs<DayUi.Go>(cards(unit = unit)[2]).wind

        assertEquals(WindRange(9, 11, WindUnit.METERS_PER_SECOND), saturdayWind(WindUnit.METERS_PER_SECOND))
        assertEquals(WindRange(33, 41, WindUnit.KILOMETERS_PER_HOUR), saturdayWind(WindUnit.KILOMETERS_PER_HOUR))
        assertEquals(WindRange(5, 6, WindUnit.BEAUFORT), saturdayWind(WindUnit.BEAUFORT))
    }

    @Test
    fun `once the time to leave has passed the card says to leave now and shows only the time left to ride`() {
        // 2 h drive and 30 min to rig: leaving at 08:00 means riding from 10:30. The morning is lighter.
        val windyToday = day(THURSDAY) { hour -> if (hour < 10) 12.0 else 18.0 }
        val today = assertIs<DayUi.Go>(cards(forecasts = mapOf("nordstrand-bay" to windyToday)).single())

        assertTrue(today.leaveNow)
        assertEquals("05:30", today.leaveBy)
        assertEquals(TimeRange("10:30", "17:00"), today.window)
        assertEquals(WindRange(18, 18, WindUnit.KNOTS), today.wind)
    }

    @Test
    fun `rides that already left are marked as departed`() {
        val rides = listOf(ride("early", departHour = 9), ride("later", departHour = 12))
        val saturday = cards(rides = CrewRides(rides, hasCrew = true, userId = me.userId), now = at(SATURDAY, 10)).first()

        assertEquals(listOf(true, false), assertIs<DayUi.Go>(saturday).rides.map { it.departed })
    }

    @Test
    fun `a spot behind the time zone of the rider does not bring back yesterday`() {
        // 00:30 on Friday in Berlin, where the rider is, is still Thursday 23:30 in London, where the spot is.
        val londonSpot = SpotWithForecast(mySpot("nordstrand-bay", zone = TimeZone.of("Europe/London")), saturdayOn())
        val noRides = CrewRides(emptyList(), hasCrew = false, userId = null)
        val cards = DayCardsMapper(alex(), listOf(londonSpot), noRides, now = at(FRIDAY, 0, 30), riderZone = BERLIN).map()

        assertEquals(listOf(DayLabel.Today, DayLabel.Tomorrow), cards.map { it.label })
        assertTrue(cards.none { it.date < FRIDAY })
    }

    @Test
    fun `day labels and departure times follow the time zone of the rider`() {
        // 23:30 in London is already Friday 00:30 in Berlin, where the spot is.
        val cards = cards(now = at(FRIDAY, 0, 30), riderZone = TimeZone.of("Europe/London"))

        assertEquals(listOf(FRIDAY, SATURDAY), cards.map { it.date })
        assertEquals(listOf(DayLabel.Tomorrow, DayLabel.Weekday(DayOfWeek.SATURDAY)), cards.map { it.label })
        assertEquals("09:30", assertIs<DayUi.Go>(cards[1]).leaveBy)
        assertEquals(TimeRange("13:00", "17:00"), assertIs<DayUi.Go>(cards[1]).window)
    }

    @Test
    fun `only today and the next two days are shown`() {
        val fourDays = saturdayOn() + day(SUNDAY) { 18.0 }

        assertEquals(listOf(THURSDAY, FRIDAY, SATURDAY), cards(forecasts = mapOf("nordstrand-bay" to fourDays)).map { it.date })
    }

    @Test
    fun `days before today in the time zone of the spot are left out`() {
        val late = cards(now = THURSDAY_8AM + 17.hours)

        assertEquals(listOf(FRIDAY, SATURDAY), late.map { it.date })
        assertEquals(listOf(DayLabel.Today, DayLabel.Tomorrow), late.map { it.label })
    }

    private companion object {
        val NAMES = mapOf("nordstrand-bay" to "Nordstrand Bay", "salt-lagoon" to "Salt Lagoon")
    }
}
