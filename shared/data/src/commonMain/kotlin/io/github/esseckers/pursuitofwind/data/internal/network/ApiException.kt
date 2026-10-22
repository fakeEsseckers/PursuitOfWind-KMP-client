package io.github.esseckers.pursuitofwind.data.internal.network

import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.ProblemDto
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/** A response outside `2xx`, with its problem body when it had a readable one. */
internal class ApiException(
    val status: Int,
    val problem: ProblemDto?,
) : Exception("HTTP $status ${problem?.code.orEmpty()}") {
    val code: String get() = problem?.code ?: "HTTP_$status"

    companion object {
        suspend fun from(response: HttpResponse): ApiException {
            val body = response.bodyAsText()
            val problem = runCatching { ContractJson.decodeFromString(ProblemDto.serializer(), body) }.getOrNull()
            return ApiException(response.status.value, problem)
        }
    }
}

/** The one place where exceptions become [AppError]s. */
internal fun Throwable.toAppError(): AppError =
    when (this) {
        is ApiException -> statusError()
        is IOException -> AppError.Network
        is SerializationException -> AppError.Unknown("Unexpected response: $message")
        else -> AppError.Unknown("${this::class.simpleName}: $message")
    }

private fun ApiException.statusError(): AppError =
    when (status) {
        UNAUTHORIZED -> AppError.Unauthorized
        CONFLICT -> AppError.Conflict(code)
        TOO_MANY_REQUESTS -> AppError.Server(status)
        in CLIENT_ERRORS -> AppError.Rejected(status, code)
        else -> AppError.Server(status)
    }

/** Runs [block] and turns any failure except cancellation into [Outcome.Failure]. */
@Suppress("TooGenericExceptionCaught") // Every failure must reach the UI as an AppError, never as a crash.
internal suspend fun <T> outcome(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

private const val UNAUTHORIZED = 401
private const val CONFLICT = 409
private const val TOO_MANY_REQUESTS = 429
private val CLIENT_ERRORS = 400..499
