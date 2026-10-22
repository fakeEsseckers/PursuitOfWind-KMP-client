package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable

/** `PUT /v1/me/push-token`. */
@Serializable
public data class PushTokenRequestDto(
    val platform: PlatformDto,
    val token: String,
)
