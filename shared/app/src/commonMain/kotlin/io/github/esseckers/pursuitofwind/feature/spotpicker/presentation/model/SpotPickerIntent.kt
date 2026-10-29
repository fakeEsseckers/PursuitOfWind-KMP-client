package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/** What the rider can ask of the spot picker. */
internal sealed interface SpotPickerIntent {
    /** Makes [spotId] a favorite, or not any more. */
    data class Toggle(
        val spotId: SpotId,
    ) : SpotPickerIntent

    /** Moves the drive to [spotId] one [step] longer or shorter. */
    data class ChangeDrive(
        val spotId: SpotId,
        val step: Step,
    ) : SpotPickerIntent

    /** Fetch the catalog again, or retry after an error; a map that did not load tries again too. */
    data object Refresh : SpotPickerIntent

    /** Show the catalog as a list or on the map. */
    data class Show(
        val view: PickerView,
    ) : SpotPickerIntent

    /** The rider tapped [spotId] on the map: it shows under the map, to choose it there. */
    data class PickOnMap(
        val spotId: SpotId,
    ) : SpotPickerIntent

    /** The rider tapped the map away from the spots: nothing is picked any more. */
    data object TapMap : SpotPickerIntent

    /** Whether the base map loaded. */
    data class BaseMap(
        val loaded: Boolean,
    ) : SpotPickerIntent

    /** Load the base map again after it failed. */
    data object RetryBaseMap : SpotPickerIntent

    /** Save the choices and leave. */
    data object Done : SpotPickerIntent
}
