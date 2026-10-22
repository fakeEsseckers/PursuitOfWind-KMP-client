package io.github.esseckers.pursuitofwind.data.network

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.data.internal.network.ApiException
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.internal.network.toAppError
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class SafeRetryTest {
    private fun key(n: Int) = "11111111-2222-4333-8444-" + n.toString().padStart(12, '0')

    /** Signs in, then answers the token refresh and every API call with [refresh] and [api]. */
    private fun TestScope.server(
        api: Pair<HttpStatusCode, List<Pair<String, String>>>,
        refresh: HttpStatusCode = HttpStatusCode.OK,
    ) = MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/v1/auth/anonymous" -> {
                        respond(TOKENS, HttpStatusCode.Created, headersOf("Content-Type", "application/json"))
                    }

                    "/v1/auth/refresh" -> {
                        respond(PROBLEM, refresh, headersOf("Content-Type", "application/problem+json"))
                    }

                    else -> {
                        respond(
                            PROBLEM,
                            api.first,
                            headersOf(
                                *(api.second + ("Content-Type" to "application/problem+json"))
                                    .map {
                                        it.first to
                                            listOf(it.second)
                                    }.toTypedArray(),
                            ),
                        )
                    }
                }
            }
        },
    )

    @Test
    fun `a write whose answer was lost is retried with the same key and applied once`() =
        runTest {
            val lost = mutableSetOf<String>()
            val device =
                NetworkHarness(
                    forwardingEngine(fakeServer()) { request, forward ->
                        val answer = forward()
                        val key = request.headers[ApiHeaders.IDEMPOTENCY_KEY]
                        if (key != null && lost.add(key)) answerLost() else answer
                    },
                )

            val created = outcome { device.api.createCrew("Retried", key(1)) }

            assertIs<Outcome.Success<*>>(created)
            assertEquals(2, device.apiSends.count { it == "POST /v1/crews" })
            assertEquals(
                1,
                device.api
                    .myCrews()
                    .items.size,
            )
        }

    @Test
    fun `the refresh request itself is never retried`() =
        runTest {
            val device =
                NetworkHarness(
                    server(
                        HttpStatusCode.Unauthorized to listOf("WWW-Authenticate" to "Bearer"),
                        refresh = HttpStatusCode.ServiceUnavailable,
                    ),
                )
            device.session.tokens()

            val result = outcome { device.api.myCrews() }

            assertEquals(AppError.Server(503), result.errorOrNull())
            assertEquals(1, device.authPaths.count { it == "/v1/auth/refresh" })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Retry-After is honoured but never past the longest back-off`() =
        runTest {
            val polite = NetworkHarness(server(HttpStatusCode.ServiceUnavailable to listOf("Retry-After" to "3")))
            val greedy = NetworkHarness(server(HttpStatusCode.ServiceUnavailable to listOf("Retry-After" to "300")))
            polite.session.tokens()
            greedy.session.tokens()

            val politeStart = testTimeSource.markNow()
            outcome { polite.api.myCrews() }
            val politeWait = politeStart.elapsedNow()
            val greedyStart = testTimeSource.markNow()
            outcome { greedy.api.myCrews() }
            val greedyWait = greedyStart.elapsedNow()

            assertEquals(9.seconds, politeWait)
            assertEquals(30.seconds, greedyWait)
        }

    @Test
    fun `cancellation is never turned into an error`() =
        runTest {
            assertFailsWith<CancellationException> { outcome { throw CancellationException("left the screen") } }
        }

    @Test
    fun `every kind of failure maps to one AppError`() {
        val byStatus = listOf(401, 403, 404, 409, 410, 422, 429, 500, 503).map { ApiException(it, null).toAppError() }

        assertEquals(
            listOf(
                AppError.Unauthorized,
                AppError.Rejected(403, "HTTP_403"),
                AppError.Rejected(404, "HTTP_404"),
                AppError.Conflict("HTTP_409"),
                AppError.Rejected(410, "HTTP_410"),
                AppError.Rejected(422, "HTTP_422"),
                AppError.Server(429),
                AppError.Server(500),
                AppError.Server(503),
            ),
            byStatus,
        )
        assertEquals(AppError.Network, IOException("reset").toAppError())
    }

    private companion object {
        const val TOKENS = """{"userId":"usr_1","accessToken":"at","refreshToken":"rt","accessTokenExpiresAt":"2030-01-01T00:00:00Z"}"""
        const val PROBLEM = """{"type":"/problems/x","title":"x","status":503,"code":"X"}"""
    }
}
