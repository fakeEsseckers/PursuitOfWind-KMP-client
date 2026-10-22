package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto

/**
 * The field limits of `api/openapi.yaml` and the cross-field rules its schema descriptions state. The real server will
 * enforce exactly these, so the fake does too; a rule that is not in the spec does not belong here.
 */
internal object Validation {
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private const val MAX_NAME = 40
    private const val MIN_WEIGHT_KG = 30.0
    private const val MAX_WEIGHT_KG = 200.0
    private const val MAX_KITES = 10
    private const val MAX_KITE_M2 = 25.0
    private val SESSION_MINUTES = 30..720
    private val PROFILE_DRIVE_MINUTES = 0..720
    private val SPOT_DRIVE_MINUTES = 0..1440
    private val SEATS = 1..8
    private const val MAX_MEETING_POINT = 120

    fun isUuid(value: String): Boolean = UUID.matches(value)

    fun requireUuid(
        value: String,
        name: String,
    ) = check422(isUuid(value)) { "$name must be a UUID, was '$value'" }

    fun requireName(
        value: String,
        name: String,
    ) = requireText(value, name, MAX_NAME)

    /** For `minLength: 1` strings without a pattern: empty is invalid, spaces are not. */
    fun requireNotEmpty(
        value: String,
        name: String,
    ) = check422(value.isNotEmpty()) { "$name must not be empty" }

    private fun requireText(
        value: String,
        name: String,
        maxLength: Int,
    ) = check422(value.hasNonSpace() && value.lengthInCodePoints() <= maxLength) {
        "$name must have 1 to $maxLength characters and not only spaces"
    }

    /**
     * The spec's `pattern: '\S'`. JSON Schema regexes count Unicode spaces such as U+00A0 as spaces, and so does
     * `isWhitespace`; Kotlin's `\S` would not (networknt, on the JDK regex engine, does not either).
     */
    private fun String.hasNonSpace(): Boolean = any { !it.isWhitespace() }

    /** JSON Schema counts code points; a Kotlin string's length counts UTF-16 units. */
    private fun String.lengthInCodePoints(): Int = length - count { it.isLowSurrogate() }

    fun requireDriveMinutes(value: Int) = check422(value in SPOT_DRIVE_MINUTES) { "driveMinutes must be from 0 to 1440, was $value" }

    fun profile(profile: RiderProfileDto) {
        requireName(profile.displayName, "displayName")
        check422(profile.weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG) { "weightKg must be between 30 and 200, was ${profile.weightKg}" }
        check422(profile.quiverM2.size <= MAX_KITES) { "quiverM2 has at most $MAX_KITES kites" }
        check422(profile.quiverM2.toSet().size == profile.quiverM2.size) { "quiverM2 lists a size twice" }
        check422(profile.quiverM2.all { it > 0.0 && it <= MAX_KITE_M2 }) { "Kite sizes must be above 0 and at most 25 m²" }
        check422(listOfNotNull(profile.minWindMps, profile.maxWindMps).all { it >= 0.0 }) { "Wind limits must not be negative" }
        val min = profile.minWindMps
        val max = profile.maxWindMps
        check422(min == null || max == null || min < max) { "minWindMps must be below maxWindMps" }
        check422(profile.minSessionMinutes in SESSION_MINUTES) { "minSessionMinutes must be from 30 to 720" }
        check422(profile.maxDriveMinutes in PROFILE_DRIVE_MINUTES) { "maxDriveMinutes must be from 0 to 720" }
    }

    fun ride(request: CreateRideRequestDto) {
        check422(request.windowStart < request.windowEnd) { "windowStart must be before windowEnd" }
        check422(request.departAt <= request.windowStart) { "departAt must not be after windowStart" }
        requireText(request.meetingPoint, "meetingPoint", MAX_MEETING_POINT)
        check422(request.seatsTotal in SEATS) { "seatsTotal must be from 1 to 8, was ${request.seatsTotal}" }
    }
}
