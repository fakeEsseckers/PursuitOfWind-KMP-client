package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Duration

/**
 * What [SpotsState] is computed from: the cached spots (null until they arrive), how the refresh is going, the
 * rider's [edits] shown on top of the cache, the spots [saving] with a save running, and the spots [removing] that are
 * hidden while they are being removed.
 */
internal data class SpotsInputs(
    val cached: Cached<List<MySpot>>? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val edits: Map<SpotId, SpotEdit> = emptyMap(),
    val saving: Set<SpotId> = emptySet(),
    val removing: Set<SpotId> = emptySet(),
)

/** Whether a refresh runs, whether one has [finished] since the tab opened, and how the last one failed. */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val finished: Boolean = false,
    val error: AppError? = null,
)

/** A followed spot's drive in minutes and favorite flag, as the rider set them. */
internal data class SpotEdit(
    val driveMinutes: Int,
    val favorite: Boolean,
)

/** A spot just removed, with what it takes to follow it again as it was. */
internal data class RemovedSpot(
    val id: SpotId,
    val name: String,
    val drive: Duration,
    val favorite: Boolean,
)
