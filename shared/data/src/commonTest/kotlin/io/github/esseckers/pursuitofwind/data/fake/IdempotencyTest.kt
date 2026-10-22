package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.AnonymousSignInRequestDto
import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.CreateCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewListDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RideDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class IdempotencyTest {
    private val crew = CreateCrewRequestDto("Retry Crew")

    private suspend fun FakeApiDriver.createCrew(
        token: String,
        key: String?,
        request: CreateCrewRequestDto = crew,
    ): FakeExchange = send(ApiRoute.CREATE_CREW, token = token, value = request, idempotencyKey = key)

    private suspend fun FakeApiDriver.crewCount(token: String): Int =
        call(ApiRoute.LIST_MY_CREWS, token = token).decode(CrewListDto.serializer()).items.size

    @Test
    fun `a retried request gets the stored response and changes nothing twice`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            val first = driver.createCrew(token, FakeApiDriver.key(1))
            val retry = driver.createCrew(token, FakeApiDriver.key(1))

            assertEquals(first.status, retry.status)
            assertEquals(first.body, retry.body)
            assertNull(first.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
            assertEquals("true", retry.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
            assertEquals(1, driver.crewCount(token))
        }

    @Test
    fun `a retried seat request does not take a second seat or fail with ALREADY_JOINED`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            driver.send(ApiRoute.JOIN_CREW, token = token, value = JoinCrewRequestDto("WIND-4KQ7"))

            val first = driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = token, idempotencyKey = FakeApiDriver.key(2))
            val retry = driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = token, idempotencyKey = FakeApiDriver.key(2))

            assertEquals(201, retry.status)
            assertEquals(first.decode(RideDto.serializer()), retry.decode(RideDto.serializer()))
        }

    @Test
    fun `the same key with a different body is rejected`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            driver.createCrew(token, FakeApiDriver.key(3))

            val reused = driver.createCrew(token, FakeApiDriver.key(3), crew.copy(name = "Other"))

            assertEquals(422, reused.status)
            assertEquals(ProblemCode.IDEMPOTENCY_KEY_REUSED, reused.problem().code)
            assertEquals(1, driver.crewCount(token))
        }

    @Test
    fun `a key is scoped to user and method and path`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn(1)
            val mira = driver.signIn(2)
            val key = FakeApiDriver.key(4)
            val follow = MySpotUpdateDto(driveMinutes = 60, favorite = true)
            driver.createCrew(alex, key)

            val otherUser = driver.createCrew(mira, key)
            val otherPath = driver.send(ApiRoute.JOIN_CREW, token = alex, value = JoinCrewRequestDto("WIND-4KQ7"), idempotencyKey = key)
            val followed = driver.send(ApiRoute.PUT_MY_SPOT, "salt-lagoon", token = alex, value = follow, idempotencyKey = key)
            val otherMethod = driver.call(ApiRoute.DELETE_MY_SPOT, "salt-lagoon", token = alex, idempotencyKey = key)

            assertEquals(listOf(201, 200, 200, 204), listOf(otherUser, otherPath, followed, otherMethod).map { it.status })
            val replayed = listOf(otherUser, otherPath, otherMethod).mapNotNull { it.headers[ApiHeaders.IDEMPOTENT_REPLAYED] }
            assertEquals(emptyList(), replayed)
            assertEquals(1, driver.crewCount(mira))
            assertEquals(2, driver.crewCount(alex))
        }

    @Test
    fun `a key is kept for 24 hours`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING)
            val driver = fakeDriver(clock)
            val key = FakeApiDriver.key(5)
            driver.createCrew(driver.signIn(), key)

            clock.advanceBy(23.hours + 59.minutes)
            val withinADay = driver.createCrew(driver.signIn(), key)
            clock.advanceBy(1.minutes)
            val afterADay = driver.createCrew(driver.signIn(), key)

            assertEquals("true", withinADay.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
            assertNull(afterADay.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
            assertEquals(2, driver.crewCount(driver.signIn()))
        }

    @Test
    fun `keys must be UUIDs`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            val response = driver.createCrew(token, "again")

            assertEquals(ProblemCode.VALIDATION_FAILED, response.problem().code)
            assertEquals(0, driver.crewCount(token))
        }

    @Test
    fun `the token endpoints ignore Idempotency-Key`() =
        runTest {
            val driver = fakeDriver()
            val request = AnonymousSignInRequestDto(FakeApiDriver.installationId(1), PlatformDto.ANDROID)

            val first = driver.send(ApiRoute.SIGN_IN_ANONYMOUSLY, token = null, value = request, idempotencyKey = "not-a-uuid")
            val second = driver.send(ApiRoute.SIGN_IN_ANONYMOUSLY, token = null, value = request, idempotencyKey = "not-a-uuid")

            assertEquals(201, first.status)
            assertEquals(200, second.status)
            assertNotEquals(first.decode(AuthTokensDto.serializer()).accessToken, second.decode(AuthTokensDto.serializer()).accessToken)
        }
}
