package io.github.esseckers.pursuitofwind.core.refresh

import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.time.Instant

/**
 * Where a screen's refresh is: one [refreshing] now, whether one has [finished] since the screen opened, how the last
 * one ended ([error], null when it worked), and when the latest one [startedAt], for screens that compare it with what
 * their cache shows (null for the others).
 */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val finished: Boolean = false,
    val error: AppError? = null,
    val startedAt: Instant? = null,
) {
    /** The error to tell the rider about: the last refresh's, while no other one runs. */
    val shownError: AppError? get() = error.takeUnless { refreshing }

    /** A refresh started, [at] that time for a screen that needs it. */
    fun started(at: Instant? = startedAt): RefreshStatus = copy(refreshing = true, startedAt = at)

    /** The refresh ended, with [error] or without one. */
    fun finished(error: AppError?): RefreshStatus = copy(refreshing = false, finished = true, error = error)
}
