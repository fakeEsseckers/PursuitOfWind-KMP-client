package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.GeoPointDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import kotlinx.datetime.TimeZone

/**
 * The field limits of `api/openapi.yaml` and the cross-field rules its schema descriptions state. The real server will
 * enforce exactly these, so the fake does too; a rule that is not in the spec does not belong here.
 */
internal object Validation {
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val SESSION_MINUTES = ApiLimits.RiderProfile.MIN_SESSION_MIN_MINUTES..ApiLimits.RiderProfile.MIN_SESSION_MAX_MINUTES
    private val PROFILE_DRIVE_MINUTES = 0..ApiLimits.RiderProfile.MAX_DRIVE_MAX_MINUTES
    private val SPOT_DRIVE_MINUTES = 0..ApiLimits.MySpot.DRIVE_MAX_MINUTES
    private val SEATS = ApiLimits.Ride.SEATS_MIN..ApiLimits.Ride.SEATS_MAX
    private val LATITUDES = -90.0..90.0
    private val LONGITUDES = -180.0..180.0
    private const val FULL_TURN_DEG = 360.0

    fun isUuid(value: String): Boolean = UUID.matches(value)

    fun requireUuid(
        value: String,
        name: String,
    ) = check422(isUuid(value)) { "$name must be a UUID, was '$value'" }

    fun requireCrewName(value: String) = requireText(value, "name", ApiLimits.Crew.NAME_MAX_LENGTH)

    /** For `minLength: 1` strings without a pattern: empty is invalid, spaces are not. */
    fun requireNotEmpty(
        value: String,
        name: String,
    ) = check422(value.isNotEmpty()) { "$name must not be empty" }

    /**
     * A spot a rider adds (`CreateSpotRequest`, with the device's [timeZone], which must be a zone this server knows) or
     * changes (`SpotUpdate`).
     */
    fun spot(
        name: String,
        location: GeoPointDto,
        waterBearingDeg: Double,
        timeZone: String? = null,
    ) {
        requireText(name, "name", ApiLimits.Spot.NAME_MAX_LENGTH)
        check422(location.lat in LATITUDES && location.lon in LONGITUDES) {
            "location must have a latitude from -90 to 90 and a longitude from -180 to 180"
        }
        check422(waterBearingDeg >= 0.0 && waterBearingDeg < FULL_TURN_DEG) { "waterBearingDeg must be from 0 up to 360" }
        check422(timeZone == null || isRegionZone(timeZone)) { "timeZone '$timeZone' is not an IANA time zone this server knows" }
    }

    fun requireDriveMinutes(value: Int) =
        check422(value in SPOT_DRIVE_MINUTES) { "driveMinutes must be from 0 to ${SPOT_DRIVE_MINUTES.last}, was $value" }

    fun profile(profile: RiderProfileDto) {
        val limits = ApiLimits.RiderProfile
        requireText(profile.displayName, "displayName", limits.DISPLAY_NAME_MAX_LENGTH)
        check422(profile.weightKg in limits.WEIGHT_MIN_KG..limits.WEIGHT_MAX_KG) {
            "weightKg must be between ${limits.WEIGHT_MIN_KG.plain()} and ${limits.WEIGHT_MAX_KG.plain()}, was ${profile.weightKg}"
        }
        check422(profile.quiverM2.size <= limits.QUIVER_MAX_KITES) { "quiverM2 has at most ${limits.QUIVER_MAX_KITES} kites" }
        check422(profile.quiverM2.toSet().size == profile.quiverM2.size) { "quiverM2 lists a size twice" }
        check422(
            profile.quiverM2.all {
                it > 0.0 && it <= limits.KITE_MAX_M2
            },
        ) { "Kite sizes must be above 0 and at most ${limits.KITE_MAX_M2.plain()} m²" }
        check422(listOfNotNull(profile.minWindMps, profile.maxWindMps).all { it >= 0.0 }) { "Wind limits must not be negative" }
        val min = profile.minWindMps
        val max = profile.maxWindMps
        check422(min == null || max == null || min < max) { "minWindMps must be below maxWindMps" }
        check422(profile.minSessionMinutes in SESSION_MINUTES) {
            "minSessionMinutes must be from ${SESSION_MINUTES.first} to ${SESSION_MINUTES.last}"
        }
        check422(profile.maxDriveMinutes in PROFILE_DRIVE_MINUTES) { "maxDriveMinutes must be from 0 to ${PROFILE_DRIVE_MINUTES.last}" }
        check422(profile.ridingFromHour in 0..limits.RIDING_FROM_HOUR_MAX) {
            "ridingFromHour must be from 0 to ${limits.RIDING_FROM_HOUR_MAX}, was ${profile.ridingFromHour}"
        }
        check422(profile.ridingToHour in limits.RIDING_TO_HOUR_MIN..limits.RIDING_TO_HOUR_MAX) {
            "ridingToHour must be from ${limits.RIDING_TO_HOUR_MIN} to ${limits.RIDING_TO_HOUR_MAX}, was ${profile.ridingToHour}"
        }
        check422(profile.ridingFromHour < profile.ridingToHour) { "ridingFromHour must be below ridingToHour" }
    }

    fun ride(request: CreateRideRequestDto) {
        check422(request.windowStart < request.windowEnd) { "windowStart must be before windowEnd" }
        check422(request.departAt <= request.windowStart) { "departAt must not be after windowStart" }
        requireText(request.meetingPoint, "meetingPoint", ApiLimits.Ride.MEETING_POINT_MAX_LENGTH)
        check422(request.seatsTotal in SEATS) { "seatsTotal must be from ${SEATS.first} to ${SEATS.last}, was ${request.seatsTotal}" }
    }

    private fun requireText(
        value: String,
        name: String,
        maxLength: Int,
    ) = check422(value.hasNonSpace() && ApiLimits.lengthOf(value) <= maxLength) {
        "$name must have 1 to $maxLength characters and not only spaces"
    }
}

/**
 * The spec's `pattern: '\S'`. JSON Schema regexes count Unicode spaces such as U+00A0 as spaces, and so does
 * `isWhitespace`; Kotlin's `\S` would not (networknt, on the JDK regex engine, does not either).
 */
private fun String.hasNonSpace(): Boolean = any { !it.isWhitespace() }

/** An IANA region id this server knows (`Europe/Berlin`), or `UTC`; not a fixed offset such as `+02:00` or `GMT+1`. */
private fun isRegionZone(id: String): Boolean = id == "UTC" || (id.contains('/') && runCatching { TimeZone.of(id) }.isSuccess)

/** `30` for 30.0, as the spec writes its limits. */
private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
