package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.ALL_MODELS
import io.github.esseckers.pursuitofwind.engine.angularDistance
import io.github.esseckers.pursuitofwind.engine.kn
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsensusTest {
    private fun wind(
        meanKn: Int,
        gustKn: Int,
        bearing: Double,
    ) = ModelWind(mean = meanKn.kn, gust = gustKn.kn, bearing = Bearing(bearing))

    @Test
    fun `A11 the mean of 350 and 10 degrees is north and never south`() {
        val bearing = vectorMeanBearing(mapOf(ModelId.ECMWF to wind(18, 24, 350.0), ModelId.ICON to wind(18, 24, 10.0)))

        assertTrue(angularDistance(Bearing(0.0), bearing) < 1e-6, "expected north, got $bearing")
    }

    @Test
    fun `models that agree keep their exact direction`() {
        val bearing = vectorMeanBearing(ALL_MODELS.associateWith { wind(18, 24, 60.0) })

        assertEquals(Bearing(60.0), bearing)
    }

    @Test
    fun `A12 opposite directions fall back to the highest-priority model present`() {
        val bearing = vectorMeanBearing(mapOf(ModelId.GFS to wind(18, 24, 270.0), ModelId.ICON to wind(18, 24, 90.0)))

        assertEquals(Bearing(90.0), bearing)
    }

    @Test
    fun `ECMWF wins the fallback when it is present`() {
        val bearing = vectorMeanBearing(mapOf(ModelId.ICON to wind(18, 24, 90.0), ModelId.ECMWF to wind(18, 24, 270.0)))

        assertEquals(Bearing(270.0), bearing)
    }

    @Test
    fun `unknown models fall back to the id that sorts first`() {
        val bearing = vectorMeanBearing(mapOf(ModelId("ukmo") to wind(18, 24, 270.0), ModelId("arpege") to wind(18, 24, 90.0)))

        assertEquals(Bearing(90.0), bearing)
    }

    @Test
    fun `speeds are arithmetic means and spread is max minus min of model means`() {
        val consensus =
            consensusWind(
                mapOf(
                    ModelId.ECMWF to wind(16, 22, 240.0),
                    ModelId.ICON to wind(18, 24, 240.0),
                    ModelId.GFS to wind(20, 29, 240.0),
                ),
            )

        assertEquals(18.0, consensus.mean.toKnots(), 1e-9)
        assertEquals(25.0, consensus.gust.toKnots(), 1e-9)
        assertEquals(4.0, consensus.modelSpread!!.toKnots(), 1e-9)
        assertTrue(angularDistance(Bearing(240.0), consensus.bearing) < 1e-6)
    }

    @Test
    fun `a single model has no spread`() {
        val consensus = consensusWind(mapOf(ModelId.ICON to wind(18, 24, 240.0)))

        assertNull(consensus.modelSpread)
        assertEquals(WindSpeed.knots(18.0), consensus.mean)
    }
}
