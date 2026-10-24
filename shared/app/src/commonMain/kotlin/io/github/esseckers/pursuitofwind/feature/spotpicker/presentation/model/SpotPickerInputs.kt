package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotPickerSnapshotEntity

/**
 * What [SpotPickerState] is computed from: the cached [snapshot] (null until it arrives), how the refresh and the save
 * are going, and the rider's [choices], which overlay the followed spots until they are saved.
 */
internal data class SpotPickerInputs(
    val snapshot: SpotPickerSnapshotEntity? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val choices: Map<SpotId, SpotChoice> = emptyMap(),
    val saving: Boolean = false,
    val saveError: AppError? = null,
)

/** Whether a refresh runs, whether one has [finished] since the picker opened, and how the last one failed. */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val finished: Boolean = false,
    val error: AppError? = null,
)

/** The rider's choice for one spot: a favorite or not, and the drive from home in minutes. */
internal data class SpotChoice(
    val selected: Boolean,
    val driveMinutes: Int,
)
