package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.AnonymousSignInRequestDto
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.RefreshTokensRequestDto
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** `POST /v1/auth/anonymous` and `POST /v1/auth/refresh`. Access tokens live one hour; refresh tokens rotate. */
internal class AuthRoutes(
    private val state: FakeState,
) {
    fun signIn(call: FakeCall): FakeResponse {
        val request = call.decode(AnonymousSignInRequestDto.serializer())
        Validation.requireUuid(request.installationId, "installationId")
        val existing = state.installations[request.installationId]
        val userId =
            existing ?: state.nextId("usr").also { id ->
                state.users[id] = User(id, "Rider ${id.takeLast(SHORT_ID_LENGTH)}")
                state.installations[request.installationId] = id
            }
        val status = if (existing == null) HttpStatusCode.Created else HttpStatusCode.OK
        return json(AuthTokensDto.serializer(), issueTokens(userId, call.now), status)
    }

    fun refresh(call: FakeCall): FakeResponse {
        val request = call.decode(RefreshTokensRequestDto.serializer())
        Validation.requireNotEmpty(request.refreshToken, "refreshToken")
        val userId = state.refreshTokens.remove(request.refreshToken) ?: return unauthorized(tokenSent = true)
        return json(AuthTokensDto.serializer(), issueTokens(userId, call.now))
    }

    /** The user behind a valid, unexpired access token. */
    fun authenticate(
        accessToken: String?,
        now: Instant,
    ): String? = accessToken?.let { state.accessTokens[it] }?.takeIf { it.expiresAt > now }?.userId

    private fun issueTokens(
        userId: String,
        now: Instant,
    ): AuthTokensDto {
        val access = state.newToken("at")
        val refresh = state.newToken("rt")
        state.accessTokens[access] = AccessGrant(userId, now + ACCESS_TOKEN_LIFETIME)
        state.refreshTokens[refresh] = userId
        return AuthTokensDto(userId, access, refresh, now + ACCESS_TOKEN_LIFETIME)
    }

    private companion object {
        val ACCESS_TOKEN_LIFETIME = 1.hours
        const val SHORT_ID_LENGTH = 4
    }
}
