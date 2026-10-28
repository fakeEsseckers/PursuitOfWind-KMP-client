package io.github.esseckers.pursuitofwind.core.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoMapperTest {
    private val nordstrand = GeoPoint(54.4936, 8.8131)

    @Test
    fun `a degree of latitude is about 111 km and a point is no distance from itself`() {
        assertEquals(111_195.0, GeoMapper.distanceMeters(GeoPoint(54.0, 8.0), GeoPoint(55.0, 8.0)), 1.0)
        assertEquals(0.0, GeoMapper.distanceMeters(nordstrand, nordstrand), 1e-9)
    }

    @Test
    fun `a degree of longitude is shorter in the north than at the equator`() {
        val equator = GeoMapper.distanceMeters(GeoPoint(0.0, 8.0), GeoPoint(0.0, 9.0))
        val north = GeoMapper.distanceMeters(GeoPoint(54.0, 8.0), GeoPoint(54.0, 9.0))

        assertEquals(111_195.0, equator, 1.0)
        assertEquals(equator * 0.5878, north, 50.0)
    }

    @Test
    fun `the four main directions`() {
        val north = GeoPoint(nordstrand.latitude + 0.01, nordstrand.longitude)
        val east = GeoPoint(nordstrand.latitude, nordstrand.longitude + 0.01)
        val south = GeoPoint(nordstrand.latitude - 0.01, nordstrand.longitude)
        val west = GeoPoint(nordstrand.latitude, nordstrand.longitude - 0.01)

        assertBearing(0.0, GeoMapper.bearing(nordstrand, north))
        assertBearing(90.0, GeoMapper.bearing(nordstrand, east))
        assertBearing(180.0, GeoMapper.bearing(nordstrand, south))
        assertBearing(270.0, GeoMapper.bearing(nordstrand, west))
    }

    @Test
    fun `across the date line the way is short and points east`() {
        val west = GeoPoint(0.0, 179.999)
        val east = GeoPoint(0.0, -179.999)

        assertEquals(222.4, GeoMapper.distanceMeters(west, east), 0.1)
        assertBearing(90.0, GeoMapper.bearing(west, east))
    }

    @Test
    fun `a longitude on another copy of the world is brought back to this one`() {
        assertEquals(
            listOf(-170.0, 120.0, -180.0, 0.0, -180.0, 179.0),
            listOf(190.0, -600.0, 540.0, 720.0, -180.0, 179.0).map(GeoMapper::normalizeLongitude),
        )
    }

    private fun assertBearing(
        expected: Double,
        actual: Bearing,
    ) {
        val off = abs(((actual.degrees - expected + 540.0) % 360.0) - 180.0)
        assertTrue(off < 0.01, "expected $expected°, was $actual")
    }
}
