package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.AnonymousSignInRequestDto
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RefreshTokensRequestDto
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

class AuthTest {
    private val request = AnonymousSignInRequestDto(FakeApiDriver.installationId(7), PlatformDto.IOS)

    @Test
    fun `the same installation signs in again as the same user`() =
        runTest {
            val driver = fakeDriver()

            val first =
                driver.send(
                    ApiRoute.SIGN_IN_ANONYMOUSLY,
                    token = null,
                    value = request,
                )
            val second =
                driver.send(
                    ApiRoute.SIGN_IN_ANONYMOUSLY,
                    token = null,
                    value = request,
                )

            assertEquals(201, first.status)
            assertEquals(200, second.status)
            assertEquals(first.decode(AuthTokensDto.serializer()).userId, second.decode(AuthTokensDto.serializer()).userId)
            assertNotEquals(first.decode(AuthTokensDto.serializer()).accessToken, second.decode(AuthTokensDto.serializer()).accessToken)
        }

    @Test
    fun `refresh tokens rotate and an old one is rejected`() =
        runTest {
            val driver = fakeDriver()
            val tokens =
                driver
                    .send(
                        ApiRoute.SIGN_IN_ANONYMOUSLY,
                        token = null,
                        value = request,
                    ).decode(AuthTokensDto.serializer())

            val refresh = RefreshTokensRequestDto(tokens.refreshToken)
            val rotated =
                driver.send(
                    ApiRoute.REFRESH_TOKENS,
                    token = null,
                    value = refresh,
                )
            val replayed =
                driver.send(
                    ApiRoute.REFRESH_TOKENS,
                    token = null,
                    value = refresh,
                )

            assertEquals(200, rotated.status)
            assertNotEquals(tokens.refreshToken, rotated.decode(AuthTokensDto.serializer()).refreshToken)
            assertEquals(401, replayed.status)
            assertEquals(ProblemCode.UNAUTHORIZED, replayed.problem().code)
        }

    @Test
    fun `access tokens expire after an hour`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING)
            val driver = fakeDriver(clock)
            val token = driver.signIn()

            clock.advanceBy(59.minutes)
            val stillValid = driver.call(ApiRoute.LIST_MY_SPOTS, token = token)
            clock.advanceBy(1.minutes)
            val expired = driver.call(ApiRoute.LIST_MY_SPOTS, token = token)

            assertEquals(200, stillValid.status)
            assertEquals(401, expired.status)
            assertEquals("Bearer error=\"invalid_token\"", expired.headers[HttpHeaders.WWWAuthenticate])
        }

    @Test
    fun `a missing token is a plain bearer challenge`() =
        runTest {
            val response = fakeDriver().call(ApiRoute.LIST_MY_CREWS)

            assertEquals(401, response.status)
            assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        }

    @Test
    fun `installation ids must be UUIDs`() =
        runTest {
            val response =
                fakeDriver().send(
                    ApiRoute.SIGN_IN_ANONYMOUSLY,
                    token = null,
                    value = request.copy(installationId = "phone-1"),
                )

            assertEquals(422, response.status)
            assertEquals(ProblemCode.VALIDATION_FAILED, response.problem().code)
        }
}
