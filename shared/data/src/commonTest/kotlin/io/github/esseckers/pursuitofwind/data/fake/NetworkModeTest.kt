package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.CreateCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewListDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NetworkModeTest {
    /** The test time source is the only way to observe virtual time; it is still experimental. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `SLOW answers after two seconds of virtual time`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            driver.server.networkMode = NetworkMode.SLOW

            val start = testTimeSource.markNow()
            val response = driver.call(ApiRoute.LIST_MY_SPOTS, token = token)

            assertEquals(200, response.status)
            assertEquals(2.seconds, start.elapsedNow())
        }

    @Test
    fun `OFFLINE fails like a missing connection`() =
        runTest {
            val driver = fakeDriver()
            driver.server.networkMode = NetworkMode.OFFLINE

            assertFailsWith<IOException> { driver.signIn() }
        }

    @Test
    fun `FLAKY fails about 30 percent of requests in the same order every run`() =
        runTest {
            suspend fun failures(): List<Int> {
                val driver = fakeDriver()
                val token = driver.signIn()
                driver.server.networkMode = NetworkMode.FLAKY
                return (0 until 100).filter { driver.call(ApiRoute.LIST_MY_SPOTS, token = token).status == 503 }
            }

            val first = failures()
            val second = failures()

            assertTrue(first.size in 20..40, "${first.size} failures")
            assertEquals(first, second)
        }

    @Test
    fun `FLAKY failures are problem bodies with Retry-After`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            driver.server.networkMode = NetworkMode.FLAKY

            val failure = (1..100).map { driver.call(ApiRoute.LIST_MY_SPOTS, token = token) }.first { it.status == 503 }

            assertEquals(ProblemCode.SERVICE_UNAVAILABLE, failure.problem().code)
            assertEquals("1", failure.headers[ApiHeaders.RETRY_AFTER])
        }

    @Test
    fun `a FLAKY failure changes nothing on the server`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            driver.server.networkMode = NetworkMode.FLAKY

            val statuses =
                (1..30).map { n ->
                    driver
                        .send(
                            ApiRoute.CREATE_CREW,
                            token = token,
                            value = CreateCrewRequestDto("Crew $n"),
                            idempotencyKey = FakeApiDriver.key(n),
                        ).status
                }
            driver.server.networkMode = NetworkMode.NORMAL
            val crews = driver.call(ApiRoute.LIST_MY_CREWS, token = token).decode(CrewListDto.serializer()).items

            assertTrue(503 in statuses)
            assertEquals(statuses.count { it == 201 }, crews.size)
        }
}
