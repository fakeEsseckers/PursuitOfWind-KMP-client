package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.Reason.COLD
import io.github.esseckers.pursuitofwind.engine.model.Reason.DARK
import io.github.esseckers.pursuitofwind.engine.model.Reason.DIRECTION_UNRIDEABLE
import io.github.esseckers.pursuitofwind.engine.model.Reason.GUSTY
import io.github.esseckers.pursuitofwind.engine.model.Reason.OFFSHORE_DANGER
import io.github.esseckers.pursuitofwind.engine.model.Reason.OVERPOWERED_FOR_QUIVER
import io.github.esseckers.pursuitofwind.engine.model.Reason.RAIN
import io.github.esseckers.pursuitofwind.engine.model.Reason.THUNDERSTORM
import io.github.esseckers.pursuitofwind.engine.model.Reason.TOO_LIGHT
import io.github.esseckers.pursuitofwind.engine.model.Reason.TOO_STRONG
import io.github.esseckers.pursuitofwind.engine.model.Reason.UNDERPOWERED_FOR_QUIVER
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.engine.model.Verdict.GO
import io.github.esseckers.pursuitofwind.engine.model.Verdict.MARGINAL
import io.github.esseckers.pursuitofwind.engine.model.Verdict.NO_GO
import kotlin.test.Test
import kotlin.test.assertEquals

class HourRulesTest {
    private data class Vector(
        val id: String,
        val profile: RiderProfile = RIDER,
        val hour: ForecastHourBuilder.() -> Unit,
        val verdict: Verdict,
        val reasons: List<Reason>,
        val kite: Double?,
    )

    private val vectors =
        listOf(
            Vector("C1", hour = { wind(18.kn, 24.kn) }, verdict = GO, reasons = emptyList(), kite = 9.0),
            Vector(
                "C2",
                hour = { wind(10.kn, 13.kn) },
                verdict = NO_GO,
                reasons = listOf(TOO_LIGHT, UNDERPOWERED_FOR_QUIVER),
                kite = null,
            ),
            Vector("C3", hour = { wind(20.kn, 31.kn) }, verdict = MARGINAL, reasons = listOf(GUSTY), kite = 7.0),
            Vector("C4", hour = { wind(20.kn, 34.kn) }, verdict = NO_GO, reasons = listOf(GUSTY), kite = 7.0),
            Vector("C5", hour = { bearing = 100.0 }, verdict = NO_GO, reasons = listOf(OFFSHORE_DANGER), kite = 9.0),
            Vector("C6", hour = { bearing = 330.0 }, verdict = NO_GO, reasons = listOf(DIRECTION_UNRIDEABLE), kite = 9.0),
            Vector("C7", hour = { precipMm = 3.0 }, verdict = MARGINAL, reasons = listOf(RAIN), kite = 9.0),
            Vector("C8", hour = { thunderRisk = ThunderRisk.HIGH }, verdict = NO_GO, reasons = listOf(THUNDERSTORM), kite = 9.0),
            Vector("C9", hour = { isDaylight = false }, verdict = NO_GO, reasons = listOf(DARK), kite = 9.0),
            Vector(
                "C10",
                hour = { wind(30.kn, 34.kn) },
                verdict = NO_GO,
                reasons = listOf(TOO_STRONG, OVERPOWERED_FOR_QUIVER),
                kite = null,
            ),
            Vector("C11", hour = { tempC = 3.0 }, verdict = MARGINAL, reasons = listOf(COLD), kite = 9.0),
            Vector(
                "C12",
                profile = RIDER.copy(minWind = 15.kn),
                hour = { wind(14.kn, 18.kn) },
                verdict = NO_GO,
                reasons = listOf(TOO_LIGHT),
                kite = 12.0,
            ),
            Vector(
                "C13",
                hour = {
                    bearing = 100.0
                    thunderRisk = ThunderRisk.HIGH
                    precipMm = 3.0
                },
                verdict = NO_GO,
                reasons = listOf(THUNDERSTORM, OFFSHORE_DANGER, RAIN),
                kite = 9.0,
            ),
        )

    @Test
    fun `C1 to C13 hour verdicts with reasons in severity order and kites`() {
        vectors.forEach { vector ->
            val result = DecisionEngine.evaluateHour(forecastHour(vector.hour), vector.profile, SPOT)

            assertEquals(vector.verdict, result.verdict, "${vector.id} verdict")
            assertEquals(vector.reasons, result.reasons, "${vector.id} reasons")
            assertEquals(vector.kite, result.recommendedKiteM2, "${vector.id} kite")
        }
    }

    @Test
    fun `wind exactly on the level limits is rideable`() {
        assertEquals(emptyList(), reasonsFor { wind(12.kn, 14.kn) }.filter { it == TOO_LIGHT })
        assertEquals(emptyList(), reasonsFor { wind(28.kn, 30.kn) }.filter { it == TOO_STRONG })
    }

    @Test
    fun `gust spread exactly 3 knots over tolerance is still MARGINAL`() {
        val result = DecisionEngine.evaluateHour(forecastHour { wind(18.kn, 31.kn) }, RIDER, SPOT)

        assertEquals(MARGINAL, result.verdict)
        assertEquals(listOf(GUSTY), result.reasons)
    }

    @Test
    fun `rain and cold thresholds are exclusive`() {
        val borderline =
            forecastHour {
                precipMm = 2.0
                tempC = 5.0
            }

        assertEquals(GO, DecisionEngine.evaluateHour(borderline, RIDER, SPOT).verdict)
    }

    @Test
    fun `level defaults set the wind range and gust tolerance`() {
        val beginner = RIDER.copy(level = SkillLevel.BEGINNER)
        val advanced = RIDER.copy(level = SkillLevel.ADVANCED)

        assertEquals(listOf(TOO_STRONG), reasonsFor(beginner) { wind(24.kn, 27.kn) })
        assertEquals(listOf(GUSTY), reasonsFor(beginner) { wind(18.kn, 27.kn) })
        assertEquals(emptyList(), reasonsFor(advanced) { wind(34.kn, 40.kn) }.filter { it == TOO_STRONG || it == GUSTY })
        assertEquals(emptyList(), reasonsFor(advanced) { wind(11.kn, 13.kn) }.filter { it == TOO_LIGHT })
    }

    @Test
    fun `personal max wind replaces the level default but gust tolerance stays`() {
        val careful = RIDER.copy(maxWind = 20.kn)

        assertEquals(listOf(TOO_STRONG), reasonsFor(careful) { wind(22.kn, 26.kn) })
        assertEquals(listOf(GUSTY), reasonsFor(careful) { wind(16.kn, 28.kn) })
    }

    @Test
    fun `personal limits replace the level defaults in both directions`() {
        assertEquals(emptyList(), reasonsFor(RIDER.copy(minWind = 10.kn)) { wind(11.kn, 13.kn) }.filter { it == TOO_LIGHT })
        assertEquals(emptyList(), reasonsFor(RIDER.copy(maxWind = 32.kn)) { wind(30.kn, 34.kn) }.filter { it == TOO_STRONG })
    }

    @Test
    fun `wind exactly on a bound shared by a rideable and a danger sector is dangerous`() {
        val sharedBound =
            SPOT.copy(
                rideableSectors = listOf(Sector.of(330.0, 60.0)),
                dangerSectors = listOf(Sector.of(60.0, 150.0)),
            )
        val straddling =
            forecastHour {
                byModel =
                    mapOf(
                        ModelId.ECMWF to ModelWind(18.kn, 24.kn, Bearing(50.0)),
                        ModelId.ICON to ModelWind(18.kn, 24.kn, Bearing(70.0)),
                    )
            }

        assertEquals(listOf(OFFSHORE_DANGER), DecisionEngine.evaluateHour(forecastHour { bearing = 60.0 }, RIDER, sharedBound).reasons)
        assertEquals(listOf(OFFSHORE_DANGER), DecisionEngine.evaluateHour(straddling, RIDER, sharedBound).reasons)
    }

    @Test
    fun `the result carries the hour start and the consensus wind`() {
        val hour = forecastHour { wind(18.kn, 24.kn) }

        val result = DecisionEngine.evaluateHour(hour, RIDER, SPOT)

        assertEquals(hour.start, result.start)
        assertEquals(18.0, result.wind.mean.toKnots(), 1e-9)
        assertEquals(0.0, result.wind.modelSpread!!.metersPerSecond, 1e-9)
    }

    @Test
    fun `reasons are declared in severity order`() {
        assertEquals(
            listOf(
                "THUNDERSTORM",
                "OFFSHORE_DANGER",
                "DARK",
                "TOO_STRONG",
                "OVERPOWERED_FOR_QUIVER",
                "GUSTY",
                "DIRECTION_UNRIDEABLE",
                "TOO_LIGHT",
                "UNDERPOWERED_FOR_QUIVER",
                "RAIN",
                "COLD",
                "TOO_FAR",
                "WINDOW_TOO_SHORT",
            ),
            Reason.entries.map { it.name },
        )
    }

    private fun reasonsFor(
        profile: RiderProfile = RIDER,
        hour: ForecastHourBuilder.() -> Unit,
    ): List<Reason> = DecisionEngine.evaluateHour(forecastHour(hour), profile, SPOT).reasons
}
