package io.github.esseckers.pursuitofwind.core.map

import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapCameraUiTest {
    @Test
    fun `no spots show the world and one spot its region`() {
        val spot = GeoPoint(54.5, 8.8)

        assertEquals(MapCameraUi.WORLD, MapCameraUi.showing(emptyList()))
        assertEquals(MapCameraUi(spot, MapCameraUi.REGION_ZOOM, fit = listOf(spot)), MapCameraUi.showing(listOf(spot)))
    }

    @Test
    fun `spots along a coast are seen together from their middle`() {
        // About 3° of longitude and 2° of latitude on the German North Sea coast.
        val coast = listOf(GeoPoint(53.5, 7.0), GeoPoint(55.5, 8.8), GeoPoint(54.3, 10.1))
        val camera = MapCameraUi.showing(coast)

        assertEquals(54.5, camera.center.latitude, 1e-9)
        assertEquals(8.55, camera.center.longitude, 1e-9)
        // A 300 dp wide map at zoom 5 sees about 6.6° of longitude.
        assertTrue(camera.zoom in 4.5..6.0, "zoom ${camera.zoom}")
        assertEquals(coast, camera.fit)
    }

    @Test
    fun `the further apart the spots the further out and never past the world`() {
        val country = MapCameraUi.showing(listOf(GeoPoint(47.0, 6.0), GeoPoint(55.0, 15.0)))
        val continents = MapCameraUi.showing(listOf(GeoPoint(-34.0, 18.4), GeoPoint(21.3, -157.8), GeoPoint(64.1, -21.9)))

        assertTrue(country.zoom < MapCameraUi.REGION_ZOOM && country.zoom > 0.0, "zoom ${country.zoom}")
        assertEquals(0.0, continents.zoom)
    }
}
