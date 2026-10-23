package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.model.SpotDetailSnapshot
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * What [SpotDetailState] is computed from: the spot, its cached [snapshot], the time [now], the refresh, and which hour
 * the rider looks at. [snapshot] and [now] are null until they first arrive.
 */
internal data class SpotDetailInputs(
    val spotId: SpotId,
    val selection: Selection = Selection.First,
    val snapshot: SpotDetailSnapshot? = null,
    val now: Instant? = null,
    val refresh: RefreshStatus = RefreshStatus(),
)

/** Which hour the details panel shows. The state mapper resolves it against the hours on the chart. */
internal sealed interface Selection {
    /** The start of the first window worth riding, or the first hour. */
    data object First : Selection

    /** The best window on [date] (local to the spot), or that day's first daylight hour. */
    data class Day(
        val date: LocalDate,
    ) : Selection

    /** The hour that starts at [start]; the first hour once it has passed. */
    data class Hour(
        val start: Instant,
    ) : Selection
}

/**
 * Where the refresh is: [refreshing] now, whether one has [finished] and with which [error]. [startedAt] is when the
 * latest one started, so the state can tell whether the cache already shows it.
 */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val startedAt: Instant? = null,
    val finished: Boolean = false,
    val error: AppError? = null,
)
