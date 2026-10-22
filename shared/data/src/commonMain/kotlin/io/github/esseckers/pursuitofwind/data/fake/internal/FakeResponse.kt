package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.ProblemDto
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationStrategy

/** A response the fake server is about to send. */
internal data class FakeResponse(
    val status: HttpStatusCode,
    val body: String = "",
    val contentType: ContentType? = null,
    val headers: List<Pair<String, String>> = emptyList(),
) {
    fun withHeader(
        name: String,
        value: String,
    ): FakeResponse = copy(headers = headers + (name to value))
}

internal val PROBLEM_JSON: ContentType = ContentType.parse(ProblemDto.MEDIA_TYPE)

internal fun <T> json(
    serializer: SerializationStrategy<T>,
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
): FakeResponse = FakeResponse(status, ContractJson.encodeToString(serializer, value), ContentType.Application.Json)

internal fun noContent(): FakeResponse = FakeResponse(HttpStatusCode.NoContent)

/** Thrown by handlers to answer with a problem body; the dispatcher turns it into the response. */
internal class ProblemException(
    val response: FakeResponse,
    cause: Throwable? = null,
) : RuntimeException(response.body, cause)

internal fun problem(
    status: HttpStatusCode,
    code: String,
    title: String,
    detail: String? = null,
    cause: Throwable? = null,
): ProblemException {
    val body = ProblemDto("/problems/" + code.lowercase().replace('_', '-'), title, status.value, code, detail)
    return ProblemException(FakeResponse(status, ContractJson.encodeToString(ProblemDto.serializer(), body), PROBLEM_JSON), cause)
}

internal fun notFound(what: String): ProblemException =
    problem(HttpStatusCode.NotFound, ProblemCode.NOT_FOUND, "Not found", "$what does not exist.")

internal fun invalid(
    detail: String,
    cause: Throwable? = null,
): ProblemException = problem(HttpStatusCode.UnprocessableEntity, ProblemCode.VALIDATION_FAILED, "Request is not valid", detail, cause)

internal fun notAMember(): ProblemException = problem(HttpStatusCode.Forbidden, ProblemCode.NOT_A_MEMBER, "Not a member of this crew")

internal fun unauthorized(tokenSent: Boolean): FakeResponse =
    problem(HttpStatusCode.Unauthorized, ProblemCode.UNAUTHORIZED, "Unauthorized")
        .response
        .withHeader(HttpHeaders.WWWAuthenticate, if (tokenSent) "Bearer error=\"invalid_token\"" else "Bearer")

internal fun serviceUnavailable(): FakeResponse =
    problem(HttpStatusCode.ServiceUnavailable, ProblemCode.SERVICE_UNAVAILABLE, "Service temporarily unavailable")
        .response
        .withHeader(ApiHeaders.RETRY_AFTER, "1")

/** Fails with `422 VALIDATION_FAILED` and [detail] unless [condition] holds. */
internal inline fun check422(
    condition: Boolean,
    detail: () -> String,
) {
    if (!condition) throw invalid(detail())
}
