package io.github.esseckers.pursuitofwind.core.ui

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.resources.error_server
import io.github.esseckers.pursuitofwind.core.resources.error_storage
import io.github.esseckers.pursuitofwind.core.resources.error_unauthorized
import io.github.esseckers.pursuitofwind.core.resources.error_unknown
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FormattingTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `clock times are local and follow daylight saving time`() {
        assertEquals("08:30", Instant.parse("2026-10-24T06:30:00Z").clockTime(berlin))
        assertEquals("07:30", Instant.parse("2026-10-26T06:30:00Z").clockTime(berlin))
        assertEquals("00:05", Instant.parse("2026-10-22T22:05:00Z").clockTime(berlin))
    }

    @Test
    fun `coordinates read latitude then longitude in degrees with five decimals and a point`() {
        assertEquals("54.49360, 8.81310", coordinatesText(GeoPoint(latitude = 54.4936, longitude = 8.8131)))
        assertEquals("-33.92490, -70.00012", coordinatesText(GeoPoint(latitude = -33.9249, longitude = -70.00012)))
        assertEquals("12.34568, 180.00000", coordinatesText(GeoPoint(latitude = 12.3456789, longitude = 179.999996)))
        // Too close to zero to show a sign.
        assertEquals("0.00000, 0.00000", coordinatesText(GeoPoint(latitude = -0.000001, longitude = 0.0)))
    }

    @Test
    fun `an hour of the day reads from midnight to the midnight that ends the day`() {
        assertEquals(listOf("00:00", "09:00", "21:00", "24:00"), listOf(0, 9, 21, 24).map(::hourOfDay))
    }

    @Test
    fun `kite sizes drop a needless fraction`() {
        assertEquals("9", formatKiteSize(9.0))
        assertEquals("10.5", formatKiteSize(10.5))
        assertEquals("12", formatKiteSize(11.96))
        assertEquals("10,5", formatKiteSize(10.5, decimalSeparator = ','))
    }

    @Test
    fun `wind speeds round to whole units`() {
        val speed = WindSpeed.knots(18.0)

        assertEquals(18, speed.rounded(WindUnit.KNOTS))
        assertEquals(9, speed.rounded(WindUnit.METERS_PER_SECOND))
        assertEquals(33, speed.rounded(WindUnit.KILOMETERS_PER_HOUR))
        assertEquals(5, speed.rounded(WindUnit.BEAUFORT))
    }

    @Test
    fun `every error has a message for the rider`() {
        assertEquals(Res.string.error_network, AppError.Network.message())
        assertEquals(Res.string.error_unauthorized, AppError.Unauthorized.message())
        assertEquals(Res.string.error_server, AppError.Server(503).message())
        assertEquals(Res.string.error_storage, AppError.Storage.message())
        assertEquals(Res.string.error_unknown, AppError.Conflict("SEATS_FULL").message())
        assertEquals(Res.string.error_unknown, AppError.Rejected(422, "VALIDATION_FAILED").message())
        assertEquals(Res.string.error_unknown, AppError.Unknown("bad body").message())
    }
}
