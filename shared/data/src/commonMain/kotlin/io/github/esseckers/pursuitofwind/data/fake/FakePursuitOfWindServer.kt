package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeApi
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeCall
import io.github.esseckers.pursuitofwind.data.fake.internal.FakeResponse
import io.github.esseckers.pursuitofwind.data.fake.internal.Router
import io.github.esseckers.pursuitofwind.data.fake.internal.notFound
import io.github.esseckers.pursuitofwind.data.fake.internal.serviceUnavailable
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
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
 * The backend the app talks to until the real one exists: a Ktor [MockEngine] that implements `api/openapi.yaml`
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

    /**
     * The engine to build the app's `HttpClient` with. Ktor's [MockEngine] keeps every request and response it has
     * seen, so memory grows with each call: fine for tests and a demo session; the data layer (#6) decides whether
     * a long-lived app needs a leaner engine or a fresh server per session.
     */
    public val engine: HttpClientEngine =
        MockEngine(
            MockEngineConfig().apply {
                if (dispatcher != null) this.dispatcher = dispatcher
                addHandler { request -> respond(serve(request)) }
            },
        )

    private suspend fun serve(request: HttpRequestData): FakeResponse {
        val mode = networkMode
        if (mode == NetworkMode.OFFLINE) throw IOException("No connection (the fake server is in OFFLINE mode)")
        if (mode == NetworkMode.SLOW) delay(SLOW_RESPONSE)
        val body = request.body.toByteArray().decodeToString()
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

    private fun MockRequestHandleScope.respond(response: FakeResponse): HttpResponseData =
        respond(
            content = response.body,
            status = response.status,
            headers =
                Headers.build {
                    response.contentType?.let { append(HttpHeaders.ContentType, it.toString()) }
                    response.headers.forEach { (name, value) -> append(name, value) }
                },
        )

    public companion object {
        /** Base URL clients use with [engine]. The host is never resolved. */
        public const val BASE_URL: String = "https://fake.pursuitofwind.local"

        private const val BEARER = "Bearer "
        private const val FLAKY_FAILURE_RATE = 0.3
        private val SLOW_RESPONSE = 2.seconds
    }
}
