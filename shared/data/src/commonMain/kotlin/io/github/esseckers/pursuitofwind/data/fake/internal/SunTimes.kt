package io.github.esseckers.pursuitofwind.data.fake.internal

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Daylight for the fake forecast's `isDaylight`: the hour starts after sunrise and ends at least 30 minutes before
 * sunset. Sunrise and sunset come from the sunrise equation, accurate to a few minutes.
 */
internal object SunTimes {
    private val SUNSET_MARGIN = 30.minutes

    fun isDaylight(
        latitude: Double,
        longitude: Double,
        hourStart: Instant,
    ): Boolean {
        val day = SolarDay(latitude, longitude, hourStart)
        val sunrise = day.sunrise()
        val sunset = day.sunset()
        val sunCrossesHorizon = sunrise != null && sunset != null
        return if (sunCrossesHorizon) hourStart >= sunrise && hourStart + 1.hours <= sunset - SUNSET_MARGIN else day.isPolarDay
    }
}

/**
 * The sunrise equation (https://en.wikipedia.org/wiki/Sunrise_equation) for the local solar day around [instant].
 * The numeric coefficients are those of the published approximation; naming each one would hide the formula.
 */
@Suppress("MagicNumber")
private class SolarDay(
    latitude: Double,
    longitude: Double,
    instant: Instant,
) {
    /** Julian day of solar noon. */
    private val transit: Double

    /** Cosine of the sun's hour angle at sunrise; outside [-1, 1] the sun does not cross the horizon that day. */
    private val cosHourAngle: Double

    init {
        val localDay = floor(instant.epochSeconds / SECONDS_PER_DAY + longitude / FULL_TURN)
        val meanSolarDay = localDay - DAYS_FROM_UNIX_EPOCH_TO_2000 - longitude / FULL_TURN
        val meanAnomaly = (357.5291 + 0.98560028 * meanSolarDay).mod(FULL_TURN).toRadians()
        val center = 1.9148 * sin(meanAnomaly) + 0.02 * sin(2 * meanAnomaly) + 0.0003 * sin(3 * meanAnomaly)
        val eclipticLongitude = (meanAnomaly.toDegrees() + center + 180.0 + 102.9372).mod(FULL_TURN).toRadians()
        transit = J2000_JULIAN_DAY + meanSolarDay + 0.0053 * sin(meanAnomaly) - 0.0069 * sin(2 * eclipticLongitude)
        val declination = asin(sin(eclipticLongitude) * sin(23.4397.toRadians()))
        val phi = latitude.toRadians()
        cosHourAngle = (sin((-0.833).toRadians()) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
    }

    /** The sun never sets: the horizon is below it all day. */
    val isPolarDay: Boolean get() = cosHourAngle < -1.0

    fun sunrise(): Instant? = hourAngleDays()?.let { julianDayToInstant(transit - it) }

    fun sunset(): Instant? = hourAngleDays()?.let { julianDayToInstant(transit + it) }

    private fun hourAngleDays(): Double? = cosHourAngle.takeIf { it in -1.0..1.0 }?.let { acos(it).toDegrees() / FULL_TURN }

    private fun julianDayToInstant(julianDay: Double): Instant =
        Instant.fromEpochMilliseconds(((julianDay - UNIX_EPOCH_JULIAN_DAY) * SECONDS_PER_DAY * 1000).toLong())

    private fun Double.toRadians(): Double = this * PI / 180.0

    private fun Double.toDegrees(): Double = this * 180.0 / PI

    private companion object {
        const val UNIX_EPOCH_JULIAN_DAY = 2_440_587.5
        const val J2000_JULIAN_DAY = 2_451_545.0
        const val DAYS_FROM_UNIX_EPOCH_TO_2000 = 10_957
        const val SECONDS_PER_DAY = 86_400.0
        const val FULL_TURN = 360.0
    }
}
