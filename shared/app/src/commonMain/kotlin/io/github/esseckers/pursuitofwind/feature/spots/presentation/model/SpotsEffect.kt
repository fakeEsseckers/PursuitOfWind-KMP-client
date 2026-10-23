package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import org.jetbrains.compose.resources.StringResource

/** One-off events of the Spots tab, handled by the app outside the screen. */
internal sealed interface SpotsEffect {
    /** Navigate to the forecast of [spotId]. */
    data class OpenSpot(
        val spotId: SpotId,
    ) : SpotsEffect

    /** Navigate to the spot picker. */
    data object AddSpots : SpotsEffect

    /** [spot] is no longer followed; offer to undo. */
    data class Removed(
        val spot: RemovedSpot,
    ) : SpotsEffect

    /** A change could not be saved and was undone on screen; [reason] says why. */
    data class SaveFailed(
        val reason: StringResource,
    ) : SpotsEffect
}
