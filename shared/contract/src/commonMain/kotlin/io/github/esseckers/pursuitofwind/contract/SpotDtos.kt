package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable

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

@Serializable
public enum class DataQualityDto {
    VERIFIED,
    UNVERIFIED,
}

@Serializable
public data class SpotDto(
    val id: String,
    val name: String,
    val location: GeoPointDto,
    val timeZone: String,
    val rideableSectors: List<SectorDto>,
    val dangerSectors: List<SectorDto>,
    val dataQuality: DataQualityDto,
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
