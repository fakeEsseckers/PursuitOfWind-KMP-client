package io.github.esseckers.pursuitofwind.data.model

import io.github.esseckers.pursuitofwind.data.internal.network.outcome

/**
 * Everything that can go wrong in a data call, as the UI sees it. Exceptions from Ktor, serialization and storage are
 * mapped to these in one place; nothing else reaches the UI.
 */
public sealed interface AppError {
    /** No connection or a timeout, after the retries a safe request gets. Cached data is still shown. */
    public data object Network : AppError

    /** The session could not be restored, not even by signing in again. */
    public data object Unauthorized : AppError

    /** `409`: the request lost a race, for example [io.github.esseckers.pursuitofwind.contract.ProblemCode.SEATS_FULL]. */
    public data class Conflict(
        val code: String,
    ) : AppError

    /** Any other `4xx` (`403`, `404`, `410`, `422`): the request cannot succeed as sent. [code] is the problem code. */
    public data class Rejected(
        val status: Int,
        val code: String,
    ) : AppError

    /** `5xx`, or `429`, still failing after the retries a safe request gets. */
    public data class Server(
        val status: Int,
    ) : AppError

    /**
     * This device could not read or write its own data (the database, DataStore, the Keychain or Keystore), for example
     * with the disk full. Only reported when the server holds nothing new: a read, a refresh, or a change that failed
     * before it was sent. A change the server made succeeds even when its answer cannot be cached; the next sync
     * caches it.
     */
    public data object Storage : AppError

    /** A bug or an unexpected response, such as a body that does not decode. [description] is for logs only. */
    public data class Unknown(
        val description: String,
    ) : AppError
}

/** The result of a data call: a value or an [AppError]. */
public sealed interface Outcome<out T> {
    public data class Success<out T>(
        val value: T,
    ) : Outcome<T>

    public data class Failure(
        val error: AppError,
    ) : Outcome<Nothing>
}

/** [Outcome.Success] with [transform] applied; failures pass through. */
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

/** The value, or null on failure. */
public fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

/** The error, or null on success. */
public fun Outcome<*>.errorOrNull(): AppError? = (this as? Outcome.Failure)?.error

/**
 * Runs [block] and turns any failure but cancellation into [Outcome.Failure], with the same [AppError] the
 * repositories give. For code above the data layer that reads the cache itself (a flow's `first()`), which can fail
 * too: a storage failure then reads as [AppError.Storage], not as a crash.
 */
public suspend fun <T> outcomeOf(block: suspend () -> T): Outcome<T> = outcome(block)
