package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
public data class GeoPointDto(
    val lat: Double,
    val lon: Double,
)

/** Clockwise arc of wind directions in degrees, both ends included; wraps past north when [fromDeg] > [toDeg]. */
@Serializable
public data class SectorDto(
    val fromDeg: Double,
    val toDeg: Double,
)

/**
 * A spot of the catalog. [waterBearingDeg] is the direction from the beach to open water; for a spot a rider adds or
 * changes, the server works the sectors out from it. Only [createdBy] can change the spot; [createdAt] is when it was
 * added, and stays when it changes.
 */
@Serializable
public data class SpotDto(
    val id: String,
    val name: String,
    val location: GeoPointDto,
    val timeZone: String,
    val rideableSectors: List<SectorDto>,
    val dangerSectors: List<SectorDto>,
    val waterBearingDeg: Double,
    val createdBy: String,
    val createdAt: Instant,
)

/**
 * `POST /v1/spots`. [timeZone] is the device's as a region id (`Europe/Berlin`) or `UTC`, a first guess the server may
 * correct from the location.
 */
@Serializable
public data class CreateSpotRequestDto(
    val name: String,
    val location: GeoPointDto,
    val waterBearingDeg: Double,
    val timeZone: String,
)

/** `PUT /v1/spots/{spotId}`, by the spot's creator only. */
@Serializable
public data class SpotUpdateDto(
    val name: String,
    val location: GeoPointDto,
    val waterBearingDeg: Double,
)

/** `GET /v1/spots/catalog`. [nextCursor] is present when more spots follow. */
@Serializable
public data class SpotPageDto(
    val items: List<SpotDto>,
    val nextCursor: String? = null,
)

/** `PUT /v1/me/spots/{spotId}`. */
@Serializable
public data class MySpotUpdateDto(
    val driveMinutes: Int,
    val favorite: Boolean,
)

@Serializable
public data class MySpotDto(
    val spot: SpotDto,
    val driveMinutes: Int,
    val favorite: Boolean,
)

/** `GET /v1/me/spots`. */
@Serializable
public data class MySpotListDto(
    val items: List<MySpotDto>,
)
