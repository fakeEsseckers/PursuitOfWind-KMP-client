package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CompassTest {
    @Test
    fun `each compass point covers 45 degrees around its direction`() {
        val cases =
            listOf(
                0.0 to Compass.N,
                22.4 to Compass.N,
                22.5 to Compass.NE,
                90.0 to Compass.E,
                200.0 to Compass.S,
                240.0 to Compass.SW,
                315.0 to Compass.NW,
                337.6 to Compass.N,
                -10.0 to Compass.N,
                370.0 to Compass.N,
            )

        cases.forEach { (bearing, compass) -> assertEquals(compass, Compass.of(bearing), "$bearing°") }
    }
}
