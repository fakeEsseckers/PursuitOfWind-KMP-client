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

    /** Fetch the catalog again, or retry after an error. */
    data object Refresh : SpotPickerIntent

    /** Save the choices and leave. */
    data object Done : SpotPickerIntent
}
