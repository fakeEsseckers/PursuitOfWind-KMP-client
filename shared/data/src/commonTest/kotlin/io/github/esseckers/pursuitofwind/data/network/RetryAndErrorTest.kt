package io.github.esseckers.pursuitofwind.data.network

import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class RetryAndErrorTest {
    private fun key(n: Int) = "11111111-2222-4333-8444-" + n.toString().padStart(12, '0')

    /** Signs anyone in, then answers every other request with [status] and [body]. */
    private fun TestScope.serverAnswering(
        status: HttpStatusCode,
        body: String = """{"type":"/problems/x","title":"x","status":${status.value},"code":"X"}""",
    ) = MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (request.url.encodedPath == "/v1/auth/anonymous") {
                    respond(TOKENS, HttpStatusCode.Created, headersOf("Content-Type", "application/json"))
                } else {
                    respond(body, status, headersOf("Content-Type", "application/problem+json"))
                }
            }
        },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `offline calls back off three times and then report Network`() =
        runTest {
            val server = fakeServer()
            val device = NetworkHarness(server.engine)
            device.api.myCrews()
            server.networkMode = NetworkMode.OFFLINE

            val start = testTimeSource.markNow()
            val result = outcome { device.api.myCrews() }

            assertEquals(AppError.Network, result.errorOrNull())
            assertEquals((500 + 1_000 + 2_000).milliseconds, start.elapsedNow())
        }

    @Test
    fun `reads and keyed writes are retried and other writes are sent once`() =
        runTest {
            val device = NetworkHarness(serverAnswering(HttpStatusCode.ServiceUnavailable))
            device.session.tokens()

            val read = outcome { device.api.myCrews() }
            val readAttempts = device.apiSends.size.also { device.apiSends.clear() }
            val write = outcome { device.api.createCrew("Crew", key(1)) }
            val writeAttempts = device.apiSends.size.also { device.apiSends.clear() }
            device.clients.api.post("/v1/crews")
            val plainAttempts = device.apiSends.size

            assertEquals(AppError.Server(503), read.errorOrNull())
            assertEquals(AppError.Server(503), write.errorOrNull())
            assertEquals(listOf(4, 4, 1), listOf(readAttempts, writeAttempts, plainAttempts))
        }

    @Test
    fun `server errors that are bugs are not retried`() =
        runTest {
            val device = NetworkHarness(serverAnswering(HttpStatusCode.InternalServerError))
            device.session.tokens()

            val result = outcome { device.api.myCrews() }

            assertEquals(AppError.Server(500), result.errorOrNull())
            assertEquals(1, device.apiSends.size)
        }

    @Test
    fun `flaky reads and keyed writes succeed through retries without duplicates`() =
        runTest {
            val server = fakeServer()
            val device = NetworkHarness(server.engine)
            device.api.myCrews()
            server.networkMode = NetworkMode.FLAKY

            val results =
                (1..10).map { n -> outcome { device.api.createCrew("Crew $n", key(n)) } } + (1..10).map { outcome { device.api.myCrews() } }
            server.networkMode = NetworkMode.NORMAL

            assertTrue(results.all { it is Outcome.Success }, results.toString())
            assertTrue(device.apiSends.size > results.size + 1, "FLAKY should have caused retries")
            assertEquals(
                10,
                device.api
                    .myCrews()
                    .items.size,
            )
        }

    @Test
    fun `problem responses map to AppError by status and code`() =
        runTest {
            val server = fakeServer()
            val device = NetworkHarness(server.engine)
            val owner = NetworkHarness(server.engine)
            device.api.joinCrew("WIND-4KQ7", key(1))
            val privateCrew = owner.api.createCrew("Private", key(2))
            val backwards = CreateRideRequestDto("salt-lagoon", THURSDAY_MORNING + 1.days, THURSDAY_MORNING, THURSDAY_MORNING, "Station", 2)
            device.api.takeSeat("rid_7f3c", key(3))

            val errors =
                listOf(
                    outcome { device.api.takeSeat("rid_7f3c", key(4)) },
                    outcome { device.api.ride("rid_nope") },
                    outcome { device.api.members(privateCrew.id) },
                    outcome { device.api.feed("crw_weekend", since = "evt_gone", limit = 10) },
                    outcome { owner.api.createRide(privateCrew.id, backwards, key(5)) },
                ).map { it.errorOrNull() }

            assertEquals(
                listOf(
                    AppError.Conflict(ProblemCode.ALREADY_JOINED),
                    AppError.Rejected(404, ProblemCode.NOT_FOUND),
                    AppError.Rejected(403, ProblemCode.NOT_A_MEMBER),
                    AppError.Rejected(410, ProblemCode.FEED_RESET),
                    AppError.Rejected(422, ProblemCode.VALIDATION_FAILED),
                ),
                errors,
            )
        }

    @Test
    fun `a body that does not decode is Unknown`() =
        runTest {
            val device = NetworkHarness(serverAnswering(HttpStatusCode.OK, body = """{"items": 7}"""))
            device.session.tokens()

            val result = outcome { device.api.myCrews() }

            assertTrue(result.errorOrNull() is AppError.Unknown, result.toString())
        }

    private companion object {
        const val TOKENS = """{"userId":"usr_1","accessToken":"at","refreshToken":"rt","accessTokenExpiresAt":"2030-01-01T00:00:00Z"}"""
    }
}
