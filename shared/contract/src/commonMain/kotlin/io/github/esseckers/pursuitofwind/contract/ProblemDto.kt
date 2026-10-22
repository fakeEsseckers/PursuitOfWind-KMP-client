package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable

/**
 * Error body of every failed API call (`application/problem+json`, RFC 9457) with a machine-readable [code].
 * Clients branch on [code] (see [ProblemCode]), never on [title] or [detail].
 */
@Serializable
public data class ProblemDto(
    val type: String,
    val title: String,
    val status: Int,
    val code: String,
    val detail: String? = null,
    val instance: String? = null,
) {
    public companion object {
        public const val MEDIA_TYPE: String = "application/problem+json"
    }
}

/** Known [ProblemDto.code] values. New codes may appear; clients fall back to the HTTP status for unknown ones. */
public object ProblemCode {
    public const val UNAUTHORIZED: String = "UNAUTHORIZED"
    public const val NOT_FOUND: String = "NOT_FOUND"
    public const val VALIDATION_FAILED: String = "VALIDATION_FAILED"
    public const val NOT_A_MEMBER: String = "NOT_A_MEMBER"
    public const val NOT_THE_DRIVER: String = "NOT_THE_DRIVER"
    public const val INVITE_INVALID: String = "INVITE_INVALID"
    public const val SEATS_FULL: String = "SEATS_FULL"
    public const val ALREADY_JOINED: String = "ALREADY_JOINED"
    public const val RIDE_CANCELLED: String = "RIDE_CANCELLED"
    public const val FEED_RESET: String = "FEED_RESET"
    public const val IDEMPOTENCY_KEY_REUSED: String = "IDEMPOTENCY_KEY_REUSED"
    public const val RATE_LIMITED: String = "RATE_LIMITED"
    public const val SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    public const val INTERNAL: String = "INTERNAL"
}
