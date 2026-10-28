package io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RemovedSpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit

/** What happened, as the reducer sees it. */
internal sealed interface SpotsResult {
    /** The cached spots, and the signed-in [riderId] to tell the spots they added. */
    data class DataChanged(
        val cached: Cached<List<MySpot>>,
        val riderId: String?,
    ) : SpotsResult

    data object RefreshStarted : SpotsResult

    data class RefreshFinished(
        val error: AppError?,
    ) : SpotsResult

    /** The rider changed [spotId] to [edit]; it shows at once, and a save of [spotId] runs until [SaveFinished]. */
    data class Edited(
        val spotId: SpotId,
        val edit: SpotEdit,
    ) : SpotsResult

    /** The save of [spotId] sent the rider's last change; [cached] was read after it, so it shows the change. */
    data class SaveFinished(
        val spotId: SpotId,
        val cached: Cached<List<MySpot>>?,
    ) : SpotsResult

    /** Saving [spotId] failed; the spot shows as cached again. */
    data class SaveFailed(
        val spotId: SpotId,
    ) : SpotsResult

    /** [spotId] is hidden while it is being removed. */
    data class RemoveStarted(
        val spotId: SpotId,
    ) : SpotsResult

    /** [spotId] is not followed any more; [cached] was read after that. */
    data class RemoveFinished(
        val spotId: SpotId,
        val cached: Cached<List<MySpot>>?,
    ) : SpotsResult

    /** Removing [spotId] failed; it shows again. */
    data class RemoveFailed(
        val spotId: SpotId,
    ) : SpotsResult

    /** A removed spot coming back after "Undo". */
    sealed interface Restore : SpotsResult

    /** [spot] shows again at once, as it was, while it is followed again. */
    data class RestoreStarted(
        val spot: RemovedSpot,
    ) : Restore

    /** [spotId] is followed again; [cached] was read after that. */
    data class RestoreFinished(
        val spotId: SpotId,
        val cached: Cached<List<MySpot>>?,
    ) : Restore

    /** Following [spotId] again failed; it goes away again. */
    data class RestoreFailed(
        val spotId: SpotId,
    ) : Restore
}
