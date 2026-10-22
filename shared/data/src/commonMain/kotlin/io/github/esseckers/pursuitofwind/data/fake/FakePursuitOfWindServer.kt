package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeApi
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeCall
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeEngine
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeResponse
import io.github.esseckers.pursuitofwind.data.fake.internal.Router
import io.github.esseckers.pursuitofwind.data.fake.internal.notFound
import io.github.esseckers.pursuitofwind.data.fake.internal.serviceUnavailable
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The backend the app talks to until the real one exists: an in-process Ktor engine that implements `api/openapi.yaml`
 * with in-memory state. The whole data layer — serialization, headers, token refresh, error mapping, retries — runs
 * for real against it; switching to the real backend means switching the engine and base URL, nothing else.
 *
 * Requests are handled one at a time. Everything is deterministic for a given [seed] and [clock], including ids,
 * invite codes, forecasts and the failures of [NetworkMode.FLAKY] (for requests sent one after another; concurrent
 * requests take the failures in the order they get the lock).
 *
 * @param dispatcher where the engine runs; tests pass a test dispatcher so [NetworkMode.SLOW] waits in virtual time.
 */
public class FakePursuitOfWindServer(
    private val clock: Clock,
    seed: Seed = Seed.default(clock.now()),
    dispatcher: CoroutineDispatcher? = null,
) {
    /** Can be switched at any time, for example from the demo panel. */
    @Volatile
    public var networkMode: NetworkMode = NetworkMode.NORMAL

    private val api = FakeApi(seed)
    private val networkRandom = Random(seed.randomSeed + 1)
    private val mutex = Mutex()

    /** The engine to build the app's `HttpClient` with. It keeps no history, so a long-running app does not grow. */
    public val engine: HttpClientEngine =
        FakeEngine(HttpClientEngineConfig().apply { if (dispatcher != null) this.dispatcher = dispatcher }) { request, body ->
            serve(request, body.decodeToString())
        }

    private suspend fun serve(
        request: HttpRequestData,
        body: String,
    ): FakeResponse {
        val mode = networkMode
        if (mode == NetworkMode.OFFLINE) throw IOException("No connection (the fake server is in OFFLINE mode)")
        if (mode == NetworkMode.SLOW) delay(SLOW_RESPONSE)
        return mutex.withLock {
            if (mode == NetworkMode.FLAKY &&
                networkRandom.nextDouble() < FLAKY_FAILURE_RATE
            ) {
                serviceUnavailable()
            } else {
                dispatch(request, body)
            }
        }
    }

    private fun dispatch(
        request: HttpRequestData,
        body: String,
    ): FakeResponse {
        val path = request.url.encodedPath.decodeURLPart()
        val match = Router.match(request.method.value, path) ?: return notFound("Route ${request.method.value} $path").response
        val authorization = request.headers[HttpHeaders.Authorization]
        val call =
            FakeCall(
                route = match.route,
                path = path,
                pathParameters = match.pathParameters,
                query =
                    request.url.parameters
                        .entries()
                        .associate { it.key to it.value.first() },
                body = body,
                bearerToken = authorization?.takeIf { it.startsWith(BEARER) }?.removePrefix(BEARER),
                idempotencyKey = request.headers[ApiHeaders.IDEMPOTENCY_KEY],
                now = clock.now(),
            )
        return api.handle(call)
    }

    public companion object {
        /** Base URL clients use with [engine]. The host is never resolved. */
        public const val BASE_URL: String = "https://fake.pursuitofwind.local"

        private const val BEARER = "Bearer "
        private const val FLAKY_FAILURE_RATE = 0.3
        private val SLOW_RESPONSE = 2.seconds
    }
}
