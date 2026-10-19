package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
public enum class ThunderRiskDto {
    LOW,
    MEDIUM,
    HIGH,
}

/** One weather model's wind for one hour, in m/s; [bearingDeg] is where the wind blows from. */
@Serializable
public data class ModelWindDto(
    val model: String,
    val meanMps: Double,
    val gustMps: Double,
    val bearingDeg: Double,
)

@Serializable
public data class ForecastHourDto(
    val start: Instant,
    val models: List<ModelWindDto>,
    val precipitationMm: Double,
    val temperatureC: Double,
    val thunderRisk: ThunderRiskDto,
    val isDaylight: Boolean,
)

/** `GET /v1/spots/{spotId}/forecast`. */
@Serializable
public data class SpotForecastDto(
    val spotId: String,
    val generatedAt: Instant,
    val hours: List<ForecastHourDto>,
)
