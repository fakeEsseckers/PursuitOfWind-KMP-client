package io.github.esseckers.pursuitofwind.core.ui

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.resources.error_server
import io.github.esseckers.pursuitofwind.core.resources.error_unauthorized
import io.github.esseckers.pursuitofwind.core.resources.error_unknown
import io.github.esseckers.pursuitofwind.data.model.AppError
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
        assertEquals(Res.string.error_unknown, AppError.Conflict("SEATS_FULL").message())
        assertEquals(Res.string.error_unknown, AppError.Rejected(422, "VALIDATION_FAILED").message())
        assertEquals(Res.string.error_unknown, AppError.Unknown("bad body").message())
    }
}
