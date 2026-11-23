package io.github.esseckers.pursuitofwind.core.link

import kotlinx.datetime.LocalDate

/**
 * What a link into the app asks for. Links come from outside: a messenger, another app, or a notification the app
 * showed itself. They are read strictly, so anything but the two shapes below is no link at all, and opening one
 * changes nothing by itself: an invitation only opens the dialog that joins.
 */
internal sealed interface AppLink {
    /** The link as text, as [parse] reads it back. */
    val uri: String

    /** `pursuitofwind://invite/WIND-4KQ7`: join the crew with the invite [code]. */
    data class Invite(
        val code: String,
    ) : AppLink {
        override val uri: String get() = "$SCHEME://$INVITE/$code"
    }

    /** `pursuitofwind://spot/nordstrand-bay?date=2026-10-24`: a spot's forecast, at [date] (local to the spot) if given. */
    data class Spot(
        val spotId: String,
        val date: LocalDate? = null,
    ) : AppLink {
        override val uri: String get() = "$SCHEME://$SPOT/$spotId" + date?.let { "?$DATE=$it" }.orEmpty()
    }

    companion object {
        const val SCHEME = "pursuitofwind"
        private const val INVITE = "invite"
        private const val SPOT = "spot"
        private const val DATE = "date"

        /** As long as the join dialog takes. */
        private const val MAX_CODE_LENGTH = 20
        private const val MAX_ID_LENGTH = 64

        /**
         * The link in [uri], or null when it is not one the app knows. No pattern matching and no decoding: the text is
         * split by hand, and every part may only hold the characters its kind allows, so nothing escaped, nested or
         * oversized gets through.
         */
        fun parse(uri: String): AppLink? {
            val rest = uri.substringAfter("://", missingDelimiterValue = "")
            val known = uri.substringBefore("://").lowercase() == SCHEME && rest.isNotEmpty()
            val parts = rest.substringBefore('?').split('/')
            val query = rest.substringAfter('?', missingDelimiterValue = "")
            return when {
                !known || parts.size != 2 -> null
                parts[0] == INVITE -> parts[1].takeIf { query.isEmpty() && it.isCode() }?.let(::Invite)
                parts[0] == SPOT -> spot(parts[1], hasQuery = '?' in rest, query = query)
                else -> null
            }
        }

        /** A spot link takes no query at all, or the one `date` with a calendar date. */
        private fun spot(
            id: String,
            hasQuery: Boolean,
            query: String,
        ): Spot? {
            val date = query.removePrefix("$DATE=").takeIf { query.startsWith("$DATE=") }?.let(LocalDate.Formats.ISO::parseOrNull)
            return if (!id.isSpotId() || (hasQuery && date == null)) null else Spot(id, date)
        }

        private fun String.isCode(): Boolean = length in 1..MAX_CODE_LENGTH && all { it.isAsciiLetterOrDigit() || it == '-' }

        /** The characters `api/openapi.yaml` allows in a spot id. */
        private fun String.isSpotId(): Boolean = length in 1..MAX_ID_LENGTH && all { it.isAsciiLetterOrDigit() || it == '-' || it == '_' }

        private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
    }
}
