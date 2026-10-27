package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Duration

/**
 * What [SpotsState] is computed from: the cached spots (null until they arrive), how the refresh is going, the
 * rider's [edits] shown on top of the cache, the spots [saving] with a save running, the spots [removing] that are
 * hidden while they are being removed, and the spots [restoring] after "Undo", shown again at once as they were until
 * they are followed again.
 */
internal data class SpotsInputs(
    val cached: Cached<List<MySpot>>? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val edits: Map<SpotId, SpotEdit> = emptyMap(),
    val saving: Set<SpotId> = emptySet(),
    val removing: Set<SpotId> = emptySet(),
    val restoring: Map<SpotId, RemovedSpot> = emptyMap(),
)

/** A followed spot's drive in minutes and favorite flag, as the rider set them. */
internal data class SpotEdit(
    val driveMinutes: Int,
    val favorite: Boolean,
) {
    companion object {
        /** How [spot] is followed now. */
        fun of(spot: MySpot): SpotEdit = SpotEdit(driveMinutes = spot.drive.inWholeMinutes.toInt(), favorite = spot.favorite)
    }
}

/** A spot just removed, with what it takes to show it and follow it again as it was. */
internal data class RemovedSpot(
    val id: SpotId,
    val name: String,
    val drive: Duration,
    val favorite: Boolean,
)
