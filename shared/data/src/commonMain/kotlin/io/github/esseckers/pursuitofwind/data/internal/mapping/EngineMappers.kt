package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.ForecastHourDto
import io.github.esseckers.pursuitofwind.contract.ModelWindDto
import io.github.esseckers.pursuitofwind.contract.SectorDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlinx.datetime.TimeZone

// API → domain and engine types, in one place: the cache's rows go through these after they are decoded, and the
// fake server's crew members use them directly to judge the wind.

internal fun SectorDto.toSector(): Sector = Sector.of(fromDeg, toDeg)

internal fun ModelWindDto.toModelWind(): ModelWind = ModelWind(WindSpeed(meanMps), WindSpeed(gustMps), Bearing(bearingDeg))

/** Throws for an unknown time zone or a broken sector, as the cache relies on (`SpotDto.toEntity`). */
internal fun SpotDto.toSpot(): Spot =
    Spot(
        id = SpotId(id),
        name = name,
        location = GeoPoint(location.lat, location.lon),
        timeZone = TimeZone.of(timeZone),
        rideableSectors = rideableSectors.map { it.toSector() },
        dangerSectors = dangerSectors.map { it.toSector() },
        water = Bearing(waterBearingDeg),
        createdBy = createdBy,
    )

internal fun ForecastHourDto.toForecastHour(): ForecastHour =
    ForecastHour(
        start = start,
        byModel = models.associate { ModelId(it.model) to it.toModelWind() },
        precipMm = precipitationMm,
        tempC = temperatureC,
        thunderRisk = thunderRisk.toModel(),
        isDaylight = isDaylight,
    )
