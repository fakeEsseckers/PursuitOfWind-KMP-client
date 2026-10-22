package io.github.esseckers.pursuitofwind.data.internal.network

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How often and how patiently safe requests are retried. Only requests that can be repeated without side effects are
 * retried: `GET`s, mutations that carry an `Idempotency-Key` (the server replays the first answer), and the anonymous
 * sign-in (the same installation id always gets the same user). The refresh request is not: refresh tokens rotate.
 *
 * The wait before a retry doubles from [baseDelay], or is the server's `Retry-After` if that is longer, and never
 * exceeds [maxDelay]. Each attempt times out after [attemptTimeout].
 */
internal data class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelay: Duration = 500.milliseconds,
    val maxDelay: Duration = 10.seconds,
    val jitter: Duration = 250.milliseconds,
    val attemptTimeout: Duration = 20.seconds,
) {
    /** The wait before retry number [retry] (from 1), given the server's `Retry-After` in seconds, if any. */
    fun delayBefore(
        retry: Int,
        retryAfterSeconds: Long?,
        random: Random = Random,
    ): Duration {
        val backoff = baseDelay * (1 shl (retry - 1).coerceIn(0, MAX_DOUBLINGS)) + jitter * random.nextDouble()
        val requested = retryAfterSeconds?.seconds ?: Duration.ZERO
        return maxOf(backoff, requested).coerceAtMost(maxDelay)
    }

    companion object {
        /** No jitter, so tests in virtual time see the same delays every run. */
        val Deterministic = RetryPolicy(jitter = Duration.ZERO)

        private const val MAX_DOUBLINGS = 16
    }
}

/**
 * The two clients the data layer uses, sharing one engine:
 * - [api] for every authenticated call: Bearer token from [AuthSession], refreshed once on `401`;
 * - [auth] for signing in and refreshing, without the auth plugin so a failing refresh cannot recurse.
 */
internal class HttpClients(
    engine: HttpClientEngine,
    baseUrl: String,
    retry: RetryPolicy,
    session: () -> AuthSession,
) {
    val auth: HttpClient = HttpClient(engine) { common(baseUrl, retry) }

    val api: HttpClient =
        HttpClient(engine) {
            common(baseUrl, retry)
            install(Auth) {
                bearer {
                    loadTokens { session().tokens().toBearer() }
                    // The token the refused request carried, not the plugin's cached one: if another request has
                    // refreshed meanwhile, the session hands out the new tokens without asking the server again.
                    refreshTokens { session().refresh(staleAccessToken = response.call.request.bearerToken()).toBearer() }
                    sendWithoutRequest { true }
                }
            }
        }

    /** Drops the tokens the auth plugin caches, after the session was cleared. */
    fun forgetTokens() {
        api.authProvider<BearerAuthProvider>()?.clearToken()
    }

    private fun HttpClientConfig<*>.common(
        baseUrl: String,
        retry: RetryPolicy,
    ) {
        expectSuccess = false
        defaultRequest { url(baseUrl) }
        install(ContentNegotiation) { json(ContractJson) }
        install(HttpRequestRetry) {
            maxRetries = retry.maxRetries
            retryIf { request, response ->
                response.status.value in RETRYABLE_STATUSES &&
                    isSafeToRepeat(request.method, request.url.encodedPath, ApiHeaders.IDEMPOTENCY_KEY in request.headers)
            }
            retryOnExceptionIf { request, cause ->
                cause is IOException &&
                    isSafeToRepeat(request.method, request.url.encodedPath, request.headers.contains(ApiHeaders.IDEMPOTENCY_KEY))
            }
            delayMillis(respectRetryAfterHeader = false) { attempt ->
                retry.delayBefore(attempt, response?.headers?.get(ApiHeaders.RETRY_AFTER)?.toLongOrNull()).inWholeMilliseconds
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = retry.attemptTimeout.inWholeMilliseconds
        }
        install(Logging) {
            logger = KermitHttpLogger
            level = LogLevel.INFO
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }

    private companion object {
        val RETRYABLE_STATUSES = setOf(429, 502, 503, 504)

        fun isSafeToRepeat(
            method: HttpMethod,
            path: String,
            hasIdempotencyKey: Boolean,
        ): Boolean = method == HttpMethod.Get || hasIdempotencyKey || path == ApiRoute.SIGN_IN_ANONYMOUSLY.path
    }
}

private fun AuthTokensDto.toBearer(): BearerTokens = BearerTokens(accessToken, refreshToken)

private fun HttpRequest.bearerToken(): String? = headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

/** Request lines and statuses only (log level INFO): no headers or bodies, so tokens never reach the log. */
private object KermitHttpLogger : io.ktor.client.plugins.logging.Logger {
    private val log = Logger.withTag("http")

    override fun log(message: String) {
        log.d { message }
    }
}
