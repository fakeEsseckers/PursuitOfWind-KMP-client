package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
public enum class PlatformDto {
    ANDROID,
    IOS,
}

/** `POST /v1/auth/anonymous`. [installationId] is a UUID the app generates once per installation. */
@Serializable
public data class AnonymousSignInRequestDto(
    val installationId: String,
    val platform: PlatformDto,
)

/** `POST /v1/auth/refresh`. */
@Serializable
public data class RefreshTokensRequestDto(
    val refreshToken: String,
)

@Serializable
public data class AuthTokensDto(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
)
