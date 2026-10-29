package io.github.esseckers.pursuitofwind.data.internal.mapping

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The profile as the settings file keeps it, the API's JSON. A level or wind unit this version does not know (written
 * by a newer version of the app, before a downgrade) reads cautiously ([cachedSkillLevel], [cachedWindUnit]), and
 * riding hours it cannot use read as the defaults. A profile that cannot be read at all is logged and null.
 */
internal fun cachedProfile(stored: String): RiderProfileDto? =
    try {
        val fields = ContractJson.parseToJsonElement(stored).jsonObject
        val enums =
            mapOf(
                "level" to JsonPrimitive(cachedSkillLevel(fields.text("level")).name),
                "windUnit" to JsonPrimitive(cachedWindUnit(fields.text("windUnit")).name),
            )
        ContractJson.decodeFromJsonElement(RiderProfileDto.serializer(), JsonObject(fields + enums)).withUsableRidingHours()
    } catch (
        // A SerializationException is one too.
        e: IllegalArgumentException,
    ) {
        Logger.withTag("Cache").w(e) { "The cached profile cannot be read" }
        null
    }

private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

/** Riding hours outside this version's rules (a later one might allow hours past midnight) become 9 to 21. */
private fun RiderProfileDto.withUsableRidingHours(): RiderProfileDto {
    val limits = ApiLimits.RiderProfile
    val usable =
        ridingFromHour in 0..limits.RIDING_FROM_HOUR_MAX &&
            ridingToHour in limits.RIDING_TO_HOUR_MIN..limits.RIDING_TO_HOUR_MAX &&
            ridingFromHour < ridingToHour
    return if (usable) this else copy(ridingFromHour = limits.RIDING_FROM_HOUR_DEFAULT, ridingToHour = limits.RIDING_TO_HOUR_DEFAULT)
}
