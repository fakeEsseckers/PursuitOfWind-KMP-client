package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.ContractJson
import kotlinx.serialization.DeserializationStrategy
import kotlin.time.Instant

/** One request as the fake's handlers see it: the matched route, its parameters and the authenticated user. */
internal data class FakeCall(
    val route: ApiRoute,
    val path: String,
    val pathParameters: Map<String, String>,
    val query: Map<String, String>,
    val body: String,
    val bearerToken: String?,
    val idempotencyKey: String?,
    val now: Instant,
    val userId: String = "",
) {
    fun pathParameter(name: String): String = pathParameters.getValue(name)

    /**
     * Decodes the JSON body, or fails with `422 VALIDATION_FAILED`. Catches [IllegalArgumentException], which covers
     * both malformed JSON ([kotlinx.serialization.SerializationException]) and `require` failures in DTO constructors.
     */
    fun <T> decode(deserializer: DeserializationStrategy<T>): T =
        try {
            ContractJson.decodeFromString(deserializer, body)
        } catch (e: IllegalArgumentException) {
            throw invalid("Body is not valid JSON for this operation: ${e.message?.lineSequence()?.firstOrNull()}", e)
        }

    /** An integer query parameter within [range], [default] when absent; `422` otherwise. */
    fun intQuery(
        name: String,
        range: IntRange,
        default: Int,
    ): Int {
        val raw = query[name] ?: return default
        return raw.toIntOrNull()?.takeIf { it in range }
            ?: throw invalid("$name must be an integer from ${range.first} to ${range.last}, was '$raw'")
    }

    /** An ISO-8601 instant query parameter, [default] when absent; `422` otherwise. */
    fun instantQuery(
        name: String,
        default: Instant,
    ): Instant {
        val raw = query[name] ?: return default
        return runCatching { Instant.parse(raw) }.getOrElse { throw invalid("$name must be an ISO-8601 instant, was '$raw'", it) }
    }
}
