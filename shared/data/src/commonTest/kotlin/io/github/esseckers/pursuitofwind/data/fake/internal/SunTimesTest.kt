package io.github.esseckers.pursuitofwind.data.fake.internal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SunTimesTest {
    private fun daylight(
        latitude: Double,
        longitude: Double,
        hour: String,
    ) = SunTimes.isDaylight(latitude, longitude, Instant.parse(hour))

    @Test
    fun `north German summer has long days`() {
        // Husum, 18 July: sunrise about 03:10Z, sunset about 19:50Z.
        assertFalse(daylight(54.49, 8.81, "2026-07-18T02:00:00Z"))
        assertTrue(daylight(54.49, 8.81, "2026-07-18T04:00:00Z"))
        assertTrue(daylight(54.49, 8.81, "2026-07-18T18:00:00Z"))
        assertFalse(daylight(54.49, 8.81, "2026-07-18T19:00:00Z"))
    }

    @Test
    fun `north German winter has short days`() {
        assertFalse(daylight(54.49, 8.81, "2026-12-21T07:00:00Z"))
        assertTrue(daylight(54.49, 8.81, "2026-12-21T11:00:00Z"))
        assertFalse(daylight(54.49, 8.81, "2026-12-21T15:00:00Z"))
    }

    @Test
    fun `polar day and polar night`() {
        assertTrue(daylight(78.2, 15.6, "2026-06-21T00:00:00Z"))
        assertFalse(daylight(78.2, 15.6, "2026-12-21T12:00:00Z"))
    }
}
