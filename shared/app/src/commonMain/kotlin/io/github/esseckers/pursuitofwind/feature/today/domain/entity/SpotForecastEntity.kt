package io.github.esseckers.pursuitofwind.feature.today.domain.entity

import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour

/** A favorite spot with its cached forecast hours. */
internal data class SpotForecastEntity(
    val spot: MySpot,
    val hours: List<ForecastHour>,
)
