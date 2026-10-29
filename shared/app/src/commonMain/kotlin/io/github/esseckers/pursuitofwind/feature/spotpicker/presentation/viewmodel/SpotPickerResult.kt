package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotPickerSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickerView

/** What happened, as the reducer sees it. */
internal sealed interface SpotPickerResult {
    data class DataChanged(
        val snapshot: SpotPickerSnapshotEntity,
    ) : SpotPickerResult

    data class Toggled(
        val spotId: SpotId,
    ) : SpotPickerResult

    /** The drive to [spotId] moved one [step]. */
    data class DriveChanged(
        val spotId: SpotId,
        val step: Step,
    ) : SpotPickerResult

    data object RefreshStarted : SpotPickerResult

    data class RefreshFinished(
        val error: AppError?,
    ) : SpotPickerResult

    data object SaveStarted : SpotPickerResult

    data class SaveFinished(
        val error: AppError?,
    ) : SpotPickerResult

    /** What happened on the map side of the picker. */
    sealed interface MapChange : SpotPickerResult {
        data class ViewChosen(
            val view: PickerView,
        ) : MapChange

        /** [spotId] picked on the map, or none. */
        data class Picked(
            val spotId: SpotId?,
        ) : MapChange

        data class BaseMapLoaded(
            val loaded: Boolean,
        ) : MapChange

        data object BaseMapRetried : MapChange
    }
}
