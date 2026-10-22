package io.github.esseckers.pursuitofwind.data.network

import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AuthSessionTest {
    @Test
    fun `the first call signs in and later calls reuse the session`() =
        runTest {
            val signedIn = mutableListOf<String>()
            val device = NetworkHarness(fakeServer().engine, onSignedIn = { userId, _ -> signedIn += userId })

            device.api.myCrews()
            device.api.mySpots()

            assertEquals(listOf("/v1/auth/anonymous"), device.authPaths)
            assertEquals(listOf("usr_0001"), signedIn)
            assertEquals("usr_0001", device.session.userId.value)
        }

    @Test
    fun `an expired access token is refreshed once and the call goes through`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING)
            val device = NetworkHarness(fakeServer(clock).engine)
            device.api.myCrews()

            clock.advanceBy(1.hours + 1.minutes)
            device.api.myCrews()

            assertEquals(listOf("/v1/auth/anonymous", "/v1/auth/refresh"), device.authPaths)
        }

    @Test
    fun `parallel calls with an expired token share one refresh`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING)
            val device = NetworkHarness(fakeServer(clock).engine)
            device.api.myCrews()

            clock.advanceBy(2.hours)
            (1..5).map { async { device.api.myCrews() } }.awaitAll()

            assertEquals(1, device.authPaths.count { it == "/v1/auth/refresh" })
        }

    @Test
    fun `a refused refresh signs in again as the same user`() =
        runTest {
            val signedIn = mutableListOf<String>()
            val device = NetworkHarness(fakeServer().engine, onSignedIn = { userId, _ -> signedIn += userId })
            device.api.myCrews()
            // As after the server forgot the session: neither token is known any more.
            val lost = AuthTokensDto("usr_0001", "at_lost", "rt_lost", THURSDAY_MORNING + 1.hours)
            device.tokens.write("session", ContractJson.encodeToString(AuthTokensDto.serializer(), lost))
            device.clients.forgetTokens()

            device.api.myCrews()

            assertEquals(listOf("/v1/auth/anonymous", "/v1/auth/refresh", "/v1/auth/anonymous"), device.authPaths)
            assertEquals(listOf("usr_0001", "usr_0001"), signedIn)
        }

    @Test
    fun `clearing the session keeps the installation and so the user`() =
        runTest {
            val device = NetworkHarness(fakeServer().engine)
            device.api.myCrews()

            device.session.clear()
            device.clients.forgetTokens()
            device.api.myCrews()

            assertEquals(listOf("/v1/auth/anonymous", "/v1/auth/anonymous"), device.authPaths)
            assertEquals("usr_0001", device.session.userId.value)
        }

    @Test
    fun `a refresh for a token that was already replaced returns the new tokens without asking again`() =
        runTest {
            val device = NetworkHarness(fakeServer().engine)
            val old = device.session.tokens()

            val first = device.session.refresh(staleAccessToken = old.accessToken)
            val second = device.session.refresh(staleAccessToken = old.accessToken)

            assertEquals(first, second)
            assertEquals(listOf("/v1/auth/anonymous", "/v1/auth/refresh"), device.authPaths)
        }
}
