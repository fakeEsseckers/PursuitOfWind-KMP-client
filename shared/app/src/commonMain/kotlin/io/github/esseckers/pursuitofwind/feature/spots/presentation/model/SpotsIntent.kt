package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/** What the rider can ask of the Spots tab. */
internal sealed interface SpotsIntent {
    /** Pull to refresh, or retry after an error. */
    data object Refresh : SpotsIntent

    /** Makes [spotId] a favorite, or not any more; saved at once. */
    data class ToggleFavorite(
        val spotId: SpotId,
    ) : SpotsIntent

    /** Moves the drive to [spotId] one [step] longer or shorter; saved at once. */
    data class ChangeDrive(
        val spotId: SpotId,
        val step: Step,
    ) : SpotsIntent

    /** Stops following [spotId]; it can be brought back with [Undo]. */
    data class Remove(
        val spotId: SpotId,
    ) : SpotsIntent

    /** Follows [spot] again as it was before it was removed. */
    data class Undo(
        val spot: RemovedSpot,
    ) : SpotsIntent

    /** Show the hourly forecast of [spotId]. */
    data class OpenSpot(
        val spotId: SpotId,
    ) : SpotsIntent

    /** Choose more spots from the catalog. */
    data object AddSpots : SpotsIntent

    /** Rename, move or turn [spotId], which the rider added, on the map. */
    data class ChangeOnMap(
        val spotId: SpotId,
    ) : SpotsIntent
}
