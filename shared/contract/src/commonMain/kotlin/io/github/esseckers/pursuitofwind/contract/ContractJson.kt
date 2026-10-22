package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * JSON settings every party uses for the API: the app's HTTP client, the in-app fake server and the future server.
 *
 * - Unknown fields are ignored, so the server can add optional fields within `/v1`.
 * - `null` optional fields are left out instead of being sent as `null`.
 * - Fields with a default value are still sent: giving a required field a default later must not drop it from the wire.
 * - Crew events carry their subtype in `type` (the kotlinx default, stated because the spec depends on it); unknown
 *   types decode as [UnknownCrewEventDto].
 */
public val ContractJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
        serializersModule =
            SerializersModule {
                polymorphic(CrewEventDto::class) {
                    defaultDeserializer { UnknownCrewEventDto.serializer() }
                }
            }
    }

/** HTTP headers defined by the contract. */
public object ApiHeaders {
    public const val IDEMPOTENCY_KEY: String = "Idempotency-Key"
    public const val IDEMPOTENT_REPLAYED: String = "Idempotent-Replayed"
    public const val RETRY_AFTER: String = "Retry-After"
}
