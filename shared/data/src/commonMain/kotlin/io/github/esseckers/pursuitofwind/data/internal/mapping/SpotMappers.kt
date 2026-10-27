package io.github.esseckers.pursuitofwind.data.internal.mapping

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.DataQualityDto
import io.github.esseckers.pursuitofwind.contract.ForecastHourDto
import io.github.esseckers.pursuitofwind.contract.ModelWindDto
import io.github.esseckers.pursuitofwind.contract.SectorDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.data.internal.local.ForecastHourEntity
import io.github.esseckers.pursuitofwind.data.internal.local.MySpotRow
import io.github.esseckers.pursuitofwind.data.internal.local.SpotEntity
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// DTO → cache entity → domain. DTOs never leave the data layer.

private val SECTORS = ListSerializer(SectorDto.serializer())
private val MODELS = ListSerializer(ModelWindDto.serializer())

/**
 * The cache row for this spot. Checked by mapping it back ([toSpot]): a spot with an unknown time zone or broken
 * sectors throws here, so it never reaches the cache, where it would break every read of the spot list.
 */
internal fun SpotDto.toEntity(): SpotEntity = toUncheckedEntity().also { it.toSpot() }

/** The valid ones of [spots]; invalid spots are logged and left out. */
internal fun validEntities(spots: List<SpotDto>): List<SpotEntity> =
    spots.mapNotNull { spot ->
        runCatching { spot.toEntity() }
            .onFailure { Logger.withTag("data").w(it) { "Spot ${spot.id} is not valid and is not cached" } }
            .getOrNull()
    }

private fun SpotDto.toUncheckedEntity(): SpotEntity =
    SpotEntity(
        id = id,
        name = name,
        latitude = location.lat,
        longitude = location.lon,
        timeZone = timeZone,
        rideableSectors = ContractJson.encodeToString(SECTORS, rideableSectors),
        dangerSectors = ContractJson.encodeToString(SECTORS, dangerSectors),
        verified = dataQuality == DataQualityDto.VERIFIED,
    )

internal fun SpotEntity.toSpot(): Spot =
    Spot(
        id = SpotId(id),
        name = name,
        location = GeoPoint(latitude, longitude),
        timeZone = TimeZone.of(timeZone),
        rideableSectors = sectors(rideableSectors),
        dangerSectors = sectors(dangerSectors),
        verified = verified,
    )

internal fun MySpotRow.toMySpot(): MySpot = MySpot(spot.toSpot(), driveMinutes.minutes, favorite)

private fun sectors(json: String): List<Sector> = ContractJson.decodeFromString(SECTORS, json).map { it.toSector() }

internal fun ForecastHourDto.toEntity(spotId: String): ForecastHourEntity =
    ForecastHourEntity(
        spotId = spotId,
        start = start.toEpochMilliseconds(),
        models = ContractJson.encodeToString(MODELS, models),
        precipitationMm = precipitationMm,
        temperatureC = temperatureC,
        thunderRisk = thunderRisk.name,
        isDaylight = isDaylight,
    )

internal fun ForecastHourEntity.toForecastHour(): ForecastHour =
    ForecastHour(
        start = Instant.fromEpochMilliseconds(start),
        byModel = ContractJson.decodeFromString(MODELS, models).associate { ModelId(it.model) to it.toModelWind() },
        precipMm = precipitationMm,
        tempC = temperatureC,
        thunderRisk = cachedThunderRisk(thunderRisk),
        isDaylight = isDaylight,
    )
