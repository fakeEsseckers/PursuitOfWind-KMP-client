package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.SpotNoGo
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Vectors G. "Now" is 07:00 in Berlin on 2026-07-18, early enough to reach every window of the day. */
class DayDecisionTest {
    private val now = Instant.parse("2026-07-18T05:00:00Z")
    private val today = LocalDate(2026, 7, 18)
    private val spotA = SPOT.copy(id = SpotId("a-beach"), name = "A Beach")
    private val spotB = SPOT.copy(id = SpotId("b-bay"), name = "B Bay")

    private fun at(time: String) = Instant.parse("${time}Z")

    private fun forecast(
        spot: SpotConfig,
        vararg hours: List<ForecastHour>,
    ) = SpotForecast(spot, hours.flatMap { it })

    /** Models disagree by 4 kn every hour, so a window gets MEDIUM confidence. */
    private val disagreeingModels: ForecastHourBuilder.() -> Unit = {
        byModel =
            mapOf(
                ModelId.ECMWF to ModelWind(16.kn, 24.kn, Bearing(240.0)),
                ModelId.ICON to ModelWind(18.kn, 24.kn, Bearing(240.0)),
                ModelId.GFS to ModelWind(20.kn, 24.kn, Bearing(240.0)),
            )
    }

    @Test
    fun `G1 the best window wins and the others become alternatives`() {
        val windowF5 = hoursFromPattern(at("2026-07-18T10:00:00"), "N G G G G N")
        val windowF6 = hoursFromPattern(at("2026-07-18T10:00:00"), "N G G M G G N", disagreeingModels)

        val decision = DecisionEngine.decideDays(listOf(forecast(spotB, windowF6), forecast(spotA, windowF5)), RIDER, now).single()

        val go = assertIs<DayDecision.Go>(decision)
        assertEquals(today, go.date)
        assertEquals(spotA.id, go.spotId)
        assertEquals(4.0, go.window.score, 1e-9)
        assertEquals(Confidence.HIGH, go.confidence)
        assertEquals(9.0, go.recommendedKiteM2)
        assertEquals(at("2026-07-18T08:30:00"), go.leaveBy)
        assertEquals(4.hours, go.usable)
        val alternative = go.alternatives.single()
        assertEquals(spotB.id, alternative.spotId)
        assertEquals(Confidence.MEDIUM, alternative.confidence)
        assertEquals(3.6125, alternative.score, 1e-9)
    }

    @Test
    fun `G2 GO hours that only form a short window give WINDOW_TOO_SHORT`() {
        val decision = DecisionEngine.decideDays(listOf(forecast(spotA, hoursFromPattern(at("2026-07-18T10:00:00"), "N G N"))), RIDER, now)

        assertEquals(
            DayDecision.NoGo(today, Reason.WINDOW_TOO_SHORT, listOf(SpotNoGo(spotA.id, Reason.WINDOW_TOO_SHORT)), nextGoDay = null),
            decision.single(),
        )
    }

    @Test
    fun `G3 the most frequent daylight reason wins and severity breaks the tie`() {
        val rider = RIDER.copy(quiverM2 = listOf(7.0, 9.0, 12.0, 15.0))
        val gusty = hoursFromPattern(at("2026-07-18T08:00:00"), "G G G G G") { wind(20.kn, 34.kn) }
        val light = hoursFromPattern(at("2026-07-18T13:00:00"), "G G G G G") { wind(11.kn, 13.kn) }
        val stormyNight =
            hoursFromPattern(at("2026-07-18T00:00:00"), "N N N N N N") {
                isDaylight = false
            }

        val decision = DecisionEngine.decideDays(listOf(forecast(spotA, stormyNight, gusty, light)), rider, now).single()

        val noGo = assertIs<DayDecision.NoGo>(decision)
        assertEquals(Reason.GUSTY, noGo.primaryReason)
        assertEquals(listOf(SpotNoGo(spotA.id, Reason.GUSTY)), noGo.details)
    }

    @Test
    fun `G4 every favorite spot beyond the maximum drive gives TOO_FAR`() {
        val far = 200.minutes
        val hours = hoursFromPattern(at("2026-07-18T10:00:00"), "G G G G")

        val decision =
            DecisionEngine
                .decideDays(listOf(forecast(spotA.copy(drive = far), hours), forecast(spotB.copy(drive = far), hours)), RIDER, now)
                .single()

        assertEquals(
            DayDecision.NoGo(
                today,
                Reason.TOO_FAR,
                listOf(SpotNoGo(spotA.id, Reason.TOO_FAR), SpotNoGo(spotB.id, Reason.TOO_FAR)),
                nextGoDay = null,
            ),
            decision,
        )
    }

    @Test
    fun `a spot that is too far is only a detail when another spot is reachable`() {
        val hours = hoursFromPattern(at("2026-07-18T10:00:00"), "N N N")

        val decision =
            DecisionEngine
                .decideDays(listOf(forecast(spotA, hours), forecast(spotB.copy(drive = 4.hours), hours)), RIDER, now)
                .single()

        val noGo = assertIs<DayDecision.NoGo>(decision)
        assertEquals(Reason.THUNDERSTORM, noGo.primaryReason)
        assertEquals(listOf(SpotNoGo(spotA.id, Reason.THUNDERSTORM), SpotNoGo(spotB.id, Reason.TOO_FAR)), noGo.details)
    }

    @Test
    fun `G5 no-go days point to the next go day`() {
        val todayHours = hoursFromPattern(at("2026-07-18T10:00:00"), "N N N")
        val tomorrowHours = hoursFromPattern(at("2026-07-19T10:00:00"), "N G N")
        val dayAfterHours = hoursFromPattern(at("2026-07-20T10:00:00"), "N G G G G N")

        val decisions = DecisionEngine.decideDays(listOf(forecast(spotA, todayHours, tomorrowHours, dayAfterHours)), RIDER, now)

        val dayAfterTomorrow = LocalDate(2026, 7, 20)
        assertEquals(listOf(today, LocalDate(2026, 7, 19), dayAfterTomorrow), decisions.map { it.date })
        assertEquals(dayAfterTomorrow, assertIs<DayDecision.NoGo>(decisions[0]).nextGoDay)
        assertEquals(dayAfterTomorrow, assertIs<DayDecision.NoGo>(decisions[1]).nextGoDay)
        assertIs<DayDecision.Go>(decisions[2])
    }

    @Test
    fun `next go day is the nearest later go and null after the last one`() {
        val go = "N G G G G N"
        val noGo = "N N N"
        val hours =
            listOf(go, noGo, go, noGo).mapIndexed { day, pattern ->
                hoursFromPattern(at("2026-07-${18 + day}T10:00:00"), pattern)
            }

        val decisions = DecisionEngine.decideDays(listOf(forecast(spotA, *hours.toTypedArray())), RIDER, now)

        assertIs<DayDecision.Go>(decisions[0])
        assertEquals(LocalDate(2026, 7, 20), assertIs<DayDecision.NoGo>(decisions[1]).nextGoDay)
        assertIs<DayDecision.Go>(decisions[2])
        assertEquals(null, assertIs<DayDecision.NoGo>(decisions[3]).nextGoDay)
    }

    @Test
    fun `days follow the spot time zone and not UTC`() {
        val lateEvening = hoursFromPattern(at("2026-07-18T21:00:00"), "N N N") { isDaylight = false }
        val justAfterMidnight = at("2026-07-18T22:30:00")

        val decisions = DecisionEngine.decideDays(listOf(forecast(spotA, lateEvening)), RIDER, justAfterMidnight)

        assertEquals(listOf(LocalDate(2026, 7, 19)), decisions.map { it.date })
    }

    @Test
    fun `a far spot with a longer forecast does not add TOO_FAR days`() {
        val reachableToday = hoursFromPattern(at("2026-07-18T10:00:00"), "N N N")
        val farTwoDays = reachableToday + hoursFromPattern(at("2026-07-19T10:00:00"), "G G G G")

        val decisions =
            DecisionEngine.decideDays(
                listOf(forecast(spotA, reachableToday), forecast(spotB.copy(drive = 4.hours), farTwoDays)),
                RIDER,
                now,
            )

        assertEquals(listOf(today), decisions.map { it.date })
        assertEquals(
            listOf(SpotNoGo(spotA.id, Reason.THUNDERSTORM), SpotNoGo(spotB.id, Reason.TOO_FAR)),
            assertIs<DayDecision.NoGo>(decisions.single()).details,
        )
    }

    @Test
    fun `a forecast may not repeat an hour`() {
        val hours = hoursFromPattern(at("2026-07-18T10:00:00"), "G G")

        assertFailsWith<IllegalArgumentException> { SpotForecast(spotA, hours + hours.first()) }
    }

    @Test
    fun `F7 equal windows go to the spot with the shorter drive`() {
        val hours = hoursFromPattern(at("2026-07-18T10:00:00"), "N G G G G N")
        val nearer = spotB.copy(drive = 60.minutes)
        val farther = spotA.copy(drive = 90.minutes)

        val go =
            assertIs<DayDecision.Go>(
                DecisionEngine.decideDays(listOf(forecast(farther, hours), forecast(nearer, hours)), RIDER, now).single(),
            )

        assertEquals(nearer.id, go.spotId)
        assertEquals(listOf(farther.id), go.alternatives.map { it.spotId })
    }

    @Test
    fun `at most two alternatives are offered`() {
        val hours = hoursFromPattern(at("2026-07-18T10:00:00"), "N G G G G N")
        val spots = listOf("a", "b", "c", "d").map { SPOT.copy(id = SpotId(it)) }

        val go = assertIs<DayDecision.Go>(DecisionEngine.decideDays(spots.map { forecast(it, hours) }, RIDER, now).single())

        assertEquals(SpotId("a"), go.spotId)
        assertEquals(listOf(SpotId("b"), SpotId("c")), go.alternatives.map { it.spotId })
    }

    @Test
    fun `days before today and night-only days`() {
        val yesterday = hoursFromPattern(at("2026-07-17T10:00:00"), "G G G G")
        val night = hoursFromPattern(at("2026-07-18T20:00:00"), "G G") { isDaylight = false }

        val decisions = DecisionEngine.decideDays(listOf(forecast(spotA, yesterday, night)), RIDER, now)

        assertEquals(listOf(today), decisions.map { it.date })
        assertEquals(Reason.DARK, assertIs<DayDecision.NoGo>(decisions.single()).primaryReason)
    }

    @Test
    fun `each spot may appear once`() {
        assertFailsWith<IllegalArgumentException> {
            DecisionEngine.decideDays(listOf(forecast(spotA), forecast(spotA)), RIDER, now)
        }
    }

    @Test
    fun `no forecasts means no decisions`() {
        assertEquals(emptyList(), DecisionEngine.decideDays(emptyList(), RIDER, now))
    }
}
