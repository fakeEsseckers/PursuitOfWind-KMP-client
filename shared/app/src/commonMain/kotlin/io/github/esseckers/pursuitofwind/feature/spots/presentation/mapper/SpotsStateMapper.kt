package io.github.esseckers.pursuitofwind.feature.spots.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.MySpotUi
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotDrive
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Notice
import kotlin.time.Duration.Companion.minutes

/** Pure rules of the Spots tab: what it shows for its inputs, with the rider's unsaved changes on top of the cache. */
internal object SpotsStateMapper {
    fun map(inputs: SpotsInputs): SpotsState {
        val refresh = inputs.refresh
        val base = SpotsState(isRefreshing = refresh.refreshing, inputs = inputs)
        val cached = inputs.cached ?: return base
        val spots = cached.value.filter { it.spot.id !in inputs.removing }.map { spotUi(it, inputs.edits[it.spot.id]) }
        val error = refresh.error.takeUnless { refresh.refreshing }
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
            notice = if (content is Content.Ready) error?.let(::notice) else null,
        )
    }

    private fun spotUi(
        spot: MySpot,
        edit: SpotEdit?,
    ): MySpotUi {
        val shown = edit ?: SpotEdit.of(spot)
        return MySpotUi(
            id = spot.spot.id,
            name = spot.spot.name,
            verified = spot.spot.verified,
            favorite = shown.favorite,
            drive = shown.driveMinutes.minutes,
            canShorten = shown.driveMinutes > 0,
            canLengthen = shown.driveMinutes < SpotDrive.MAX_STEPPED_MINUTES,
            saving = edit != null,
        )
    }

    private fun notice(error: AppError): Notice = if (error == AppError.Network) Notice.OFFLINE else Notice.REFRESH_FAILED
}
