package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotPickerSnapshotEntity

/**
 * What [SpotPickerState] is computed from: the cached [snapshot] (null until it arrives), how the refresh and the save
 * are going, and the rider's [choices], which overlay the followed spots until they are saved; then the [view], the
 * spot picked on the map ([mapSpot]) and how the map's base map is doing ([baseMapFailed], [baseMapAttempt]).
 */
internal data class SpotPickerInputs(
    val snapshot: SpotPickerSnapshotEntity? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val choices: Map<SpotId, SpotChoice> = emptyMap(),
    val saving: Boolean = false,
    val saveError: AppError? = null,
    val view: PickerView = PickerView.LIST,
    val mapSpot: SpotId? = null,
    val baseMapFailed: Boolean = false,
    val baseMapAttempt: Int = 0,
)

/** The rider's choice for one spot: a favorite or not, and the drive from home in minutes. */
internal data class SpotChoice(
    val selected: Boolean,
    val driveMinutes: Int,
)
