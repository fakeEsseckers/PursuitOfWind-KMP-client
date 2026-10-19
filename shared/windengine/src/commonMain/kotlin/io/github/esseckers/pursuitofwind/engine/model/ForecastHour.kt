package io.github.esseckers.pursuitofwind.engine.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** Weather model that produced a forecast, e.g. `ecmwf`. */
@JvmInline
public value class ModelId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Model id must not be blank" }
    }

    override fun toString(): String = value

    public companion object {
        public val ECMWF: ModelId = ModelId("ecmwf")
        public val ICON: ModelId = ModelId("icon")
        public val GFS: ModelId = ModelId("gfs")

        /** Whose direction wins when the models' directions cancel out. */
        internal val PRIORITY: List<ModelId> = listOf(ECMWF, ICON, GFS)
    }
}

/** One model's wind for one hour. Gust below mean is accepted (some providers report it); it never makes an hour GUSTY. */
public data class ModelWind(
    public val mean: WindSpeed,
    public val gust: WindSpeed,
    public val bearing: Bearing,
)

public enum class ThunderRisk {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Forecast for the hour that starts at [start] and lasts one hour.
 *
 * @property byModel wind per model; at least one model.
 * @property precipMm precipitation during the hour.
 * @property isDaylight false if the hour starts before sunrise or ends later than 30 minutes before sunset.
 *   The data layer computes it from the spot's sun times.
 */
public data class ForecastHour(
    public val start: Instant,
    public val byModel: Map<ModelId, ModelWind>,
    public val precipMm: Double,
    public val tempC: Double,
    public val thunderRisk: ThunderRisk,
    public val isDaylight: Boolean,
) {
    init {
        require(byModel.isNotEmpty()) { "Forecast hour $start has no model data" }
        require(precipMm.isFinite() && precipMm >= 0.0) { "Precipitation must be a non-negative number of mm, was $precipMm" }
        require(tempC.isFinite()) { "Temperature must be finite, was $tempC" }
    }
}
