package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.core.ui.DEFAULT_SPOT_DRIVE_MINUTES
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity

/**
 * What [SpotMapState] is computed from.
 *
 * @property snapshot the cached spots and rider; null until it arrives.
 * @property editing the spot the rider changes, which they added; null while adding or following one.
 * @property editOnly the map was opened to change [editing] (from the Spots tab): it stays on that spot, and back is
 *   the way out.
 * @property draft the spot being placed or changed: its pin, water and name.
 * @property selected the spot whose marker the rider tapped.
 * @property nearbyDismissed the rider wants a new spot at the pin although one is near it.
 * @property driveMinutes the drive to follow a new or selected spot with.
 * @property baseMapFailed the base map could not load: without it the map shows nothing, not even the spots.
 * @property baseMapAttempt how many times the rider asked to load the base map again.
 */
internal data class SpotMapInputs(
    val snapshot: SpotMapSnapshotEntity? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val editing: SpotId? = null,
    val editOnly: Boolean = false,
    val draft: SpotDraft? = null,
    val selected: SpotId? = null,
    val nearbyDismissed: Boolean = false,
    val driveMinutes: Int = DEFAULT_SPOT_DRIVE_MINUTES,
    val saving: Boolean = false,
    val saveError: AppError? = null,
    val baseMapFailed: Boolean = false,
    val baseMapAttempt: Int = 0,
)

/**
 * A spot being placed: its [pin], where its [water] lies (null until the rider taps it) and its [name]. [nameVersion]
 * changes when the name is set from outside the field (a spot to change), so the field shows it.
 */
internal data class SpotDraft(
    val pin: GeoPoint,
    val water: Bearing? = null,
    val name: String = "",
    val nameVersion: Int = 0,
)
