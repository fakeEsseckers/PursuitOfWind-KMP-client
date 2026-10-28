package io.github.esseckers.pursuitofwind.feature.spots.presentation.mapper

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNoticeMapper
import io.github.esseckers.pursuitofwind.core.ui.SPOT_DRIVE_STEPS
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.MySpotUi
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RemovedSpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
import kotlin.time.Duration.Companion.minutes

/** Pure rules of the Spots tab: what it shows for its inputs, with the rider's unsaved changes on top of the cache. */
internal object SpotsStateMapper {
    fun map(inputs: SpotsInputs): SpotsState {
        val refresh = inputs.refresh
        val base = SpotsState(isRefreshing = refresh.refreshing, inputs = inputs)
        val cached = inputs.cached ?: return base
        val listed =
            cached.value
                .filter { it.spot.id !in inputs.removing }
                .map { spotUi(it, inputs.edits[it.spot.id], restoring = it.spot.id in inputs.restoring, riderId = inputs.riderId) }
        // A spot coming back after "Undo" shows at once, in its place by name, until the cache holds it.
        val returning =
            inputs.restoring.values
                .filter { spot -> listed.none { it.id == spot.id } }
                .map(::returningUi)
        val spots = if (returning.isEmpty()) listed else (listed + returning).sortedBy { it.name }
        val error = refresh.shownError
        val content =
            when {
                spots.isNotEmpty() -> Content.Ready(spots)
                error != null && cached.updatedAt == null -> Content.Error(error.message())
                refresh.finished || cached.updatedAt != null -> Content.Empty
                else -> Content.Loading
            }
        return base.copy(
            content = content,
            // Cached spots win over an error: the list stays, with a notice above it.
            notice = if (content is Content.Ready) RefreshNoticeMapper.notice(error) else null,
        )
    }

    private fun spotUi(
        spot: MySpot,
        edit: SpotEdit?,
        restoring: Boolean,
        riderId: String?,
    ): MySpotUi {
        val shown = edit ?: SpotEdit.of(spot)
        val drive = SPOT_DRIVE_STEPS.stepper(shown.driveMinutes)
        return MySpotUi(
            id = spot.spot.id,
            name = spot.spot.name,
            favorite = shown.favorite,
            drive = shown.driveMinutes.minutes,
            canShorten = drive.canDecrease,
            canLengthen = drive.canIncrease,
            saving = edit != null,
            restoring = restoring,
            mine = riderId != null && spot.spot.createdBy == riderId,
        )
    }

    /** A removed spot as it was, while it is followed again; it cannot change until it is. */
    private fun returningUi(spot: RemovedSpot): MySpotUi =
        MySpotUi(
            id = spot.id,
            name = spot.name,
            favorite = spot.favorite,
            drive = spot.drive,
            canShorten = false,
            canLengthen = false,
            saving = false,
            restoring = true,
            // It cannot change until it is followed again.
            mine = false,
        )
}
