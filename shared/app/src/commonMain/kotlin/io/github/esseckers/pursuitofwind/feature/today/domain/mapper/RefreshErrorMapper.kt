package io.github.esseckers.pursuitofwind.feature.today.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.AppError

/** Which of several refresh errors the rider is told about. */
internal object RefreshErrorMapper {
    /** The error to show for [errors]: a connection problem first, since it explains the others; null when none failed. */
    fun mainError(errors: List<AppError>): AppError? = errors.firstOrNull { it == AppError.Network } ?: errors.firstOrNull()
}
