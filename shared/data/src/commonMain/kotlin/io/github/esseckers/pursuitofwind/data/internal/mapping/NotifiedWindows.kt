package io.github.esseckers.pursuitofwind.data.internal.mapping

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

// The windows the rider was told about, as the settings file keeps them: a JSON array, times in epoch seconds.

internal fun List<NotifiedWindow>.toStored(): String =
    buildJsonArray {
        forEach { window ->
            add(
                buildJsonObject {
                    put(SPOT, window.spotId.value)
                    put(DATE, window.date.toString())
                    put(START, window.start.epochSeconds)
                    put(END, window.end.epochSeconds)
                },
            )
        }
    }.toString()

/**
 * The windows in [stored]. An entry that cannot be read is left out, and a value that cannot be read at all is logged
 * and empty: the worst that follows is one window announced a second time.
 */
internal fun notifiedWindows(stored: String): List<NotifiedWindow> =
    try {
        ContractJson.parseToJsonElement(stored).jsonArray.mapNotNull(::notifiedWindow)
    } catch (
        // A SerializationException is one too.
        e: IllegalArgumentException,
    ) {
        Logger.withTag("Cache").w(e) { "The windows the rider was told about cannot be read" }
        emptyList()
    }

private fun notifiedWindow(entry: JsonElement): NotifiedWindow? {
    val fields = entry as? JsonObject ?: return null
    val spot = fields.text(SPOT)?.takeIf { it.isNotBlank() }
    val date = fields.text(DATE)?.let(LocalDate.Formats.ISO::parseOrNull)
    val times = listOfNotNull(fields.seconds(START), fields.seconds(END)).map(Instant::fromEpochSeconds)
    return if (spot == null || date == null || times.size != 2) null else NotifiedWindow(SpotId(spot), date, times[0], times[1])
}

private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.seconds(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull

private const val SPOT = "spotId"
private const val DATE = "date"
private const val START = "start"
private const val END = "end"
