package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable

/**
 * Error body of every failed API call (`application/problem+json`, RFC 9457) with a machine-readable [code].
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
