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

/** The error to show for [errors]: a connection problem first, since it explains the others. */
internal fun mainError(errors: List<AppError>): AppError? = errors.firstOrNull { it == AppError.Network } ?: errors.firstOrNull()
