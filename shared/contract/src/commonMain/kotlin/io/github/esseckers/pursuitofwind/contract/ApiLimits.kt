package io.github.esseckers.pursuitofwind.contract

/**
 * The field limits and defaults of `api/openapi.yaml`, for the client's forms and the fake server's validation;
 * `ApiLimitsContractTest` keeps them equal to the spec. Text lengths count Unicode code points, as JSON Schema does: an
 * emoji is one character, though a Kotlin string holds it as two.
 */
public object ApiLimits {
    /** `RiderProfile`. */
    public object RiderProfile {
        public const val DISPLAY_NAME_MAX_LENGTH: Int = 40
        public const val WEIGHT_MIN_KG: Double = 30.0
        public const val WEIGHT_MAX_KG: Double = 200.0
        public const val QUIVER_MAX_KITES: Int = 10

        /** The largest kite; a size must also be above zero. */
        public const val KITE_MAX_M2: Double = 25.0
        public const val MIN_SESSION_MIN_MINUTES: Int = 30
        public const val MIN_SESSION_MAX_MINUTES: Int = 720
        public const val MAX_DRIVE_MAX_MINUTES: Int = 720
        public const val RIDING_FROM_HOUR_MAX: Int = 23
        public const val RIDING_TO_HOUR_MIN: Int = 1
        public const val RIDING_TO_HOUR_MAX: Int = 24

        /** The riding hours of a profile that does not give them: 9:00 to 21:00. */
        public const val RIDING_FROM_HOUR_DEFAULT: Int = 9
        public const val RIDING_TO_HOUR_DEFAULT: Int = 21
    }

    /** `CreateSpotRequest` and `SpotUpdate`. */
    public object Spot {
        public const val NAME_MAX_LENGTH: Int = 40
    }

    /** `MySpot` and `MySpotUpdate`. */
    public object MySpot {
        public const val DRIVE_MAX_MINUTES: Int = 1440
    }

    /** `CreateCrewRequest`. */
    public object Crew {
        public const val NAME_MAX_LENGTH: Int = 40
    }

    /** `CreateRideRequest` and `Ride`. */
    public object Ride {
        public const val MEETING_POINT_MAX_LENGTH: Int = 120
        public const val SEATS_MIN: Int = 1
        public const val SEATS_MAX: Int = 8
    }

    /** The `limit` query parameter of every paged list. */
    public object Paging {
        public const val LIMIT_MAX: Int = 100
    }

    /** The `hours` query parameter of a spot's forecast. */
    public object Forecast {
        public const val HOURS_MAX: Int = 168
    }

    /** The length of [text] as the API counts it, in code points. */
    public fun lengthOf(text: String): Int {
        var index = 0
        var count = 0
        while (index < text.length) {
            index += charsOfCodePointAt(text, index)
            count++
        }
        return count
    }

    /** [text] cut to at most [max] code points, never between the two halves of one. */
    public fun cut(
        text: String,
        max: Int,
    ): String {
        var end = 0
        var count = 0
        while (end < text.length && count < max) {
            end += charsOfCodePointAt(text, end)
            count++
        }
        return text.substring(0, end)
    }

    /** 2 for the two halves of a code point beyond the 16-bit range, else 1 (a stray half counts on its own). */
    private fun charsOfCodePointAt(
        text: String,
        index: Int,
    ): Int = if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1
}
