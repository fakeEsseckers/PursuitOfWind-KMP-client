package io.github.esseckers.pursuitofwind.data.internal.mapping

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The profile as the settings file keeps it, the API's JSON. A level or wind unit this version does not know (written
 * by a newer version of the app, before a downgrade) reads cautiously ([cachedSkillLevel], [cachedWindUnit]). A profile
 * that cannot be read at all is logged and null.
 */
internal fun cachedProfile(stored: String): RiderProfileDto? =
    try {
        val fields = ContractJson.parseToJsonElement(stored).jsonObject
        val enums =
            mapOf(
                "level" to JsonPrimitive(cachedSkillLevel(fields.text("level")).name),
                "windUnit" to JsonPrimitive(cachedWindUnit(fields.text("windUnit")).name),
            )
        ContractJson.decodeFromJsonElement(RiderProfileDto.serializer(), JsonObject(fields + enums))
    } catch (
        // A SerializationException is one too.
        e: IllegalArgumentException,
    ) {
        Logger.withTag("Cache").w(e) { "The cached profile cannot be read" }
        null
    }

private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
