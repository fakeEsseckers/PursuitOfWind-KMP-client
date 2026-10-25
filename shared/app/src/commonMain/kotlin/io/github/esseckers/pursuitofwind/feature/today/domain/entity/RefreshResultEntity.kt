package io.github.esseckers.pursuitofwind.feature.today.domain.entity

import io.github.esseckers.pursuitofwind.data.model.AppError

/**
 * How a refresh went. Forecast, spot and profile problems affect the answers; crew problems only the rides, so they
 * are kept apart.
 */
internal data class RefreshResultEntity(
    val forecastError: AppError? = null,
    val crewError: AppError? = null,
)
