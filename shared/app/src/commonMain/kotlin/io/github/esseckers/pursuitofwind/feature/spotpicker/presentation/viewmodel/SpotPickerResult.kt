package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotPickerSnapshotEntity

/** What happened, as the reducer sees it. */
internal sealed interface SpotPickerResult {
    data class DataChanged(
        val snapshot: SpotPickerSnapshotEntity,
    ) : SpotPickerResult

    data class Toggled(
        val spotId: SpotId,
    ) : SpotPickerResult

    data class DriveChanged(
        val spotId: SpotId,
        val minutes: Int,
    ) : SpotPickerResult

    data object RefreshStarted : SpotPickerResult

    data class RefreshFinished(
        val error: AppError?,
    ) : SpotPickerResult

    data object SaveStarted : SpotPickerResult

    data class SaveFinished(
        val error: AppError?,
    ) : SpotPickerResult
}
