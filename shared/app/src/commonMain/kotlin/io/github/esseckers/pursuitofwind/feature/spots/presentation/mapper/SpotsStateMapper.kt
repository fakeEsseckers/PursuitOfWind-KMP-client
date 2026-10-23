package io.github.esseckers.pursuitofwind.feature.spots.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.MySpotUi
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Notice
import kotlin.time.Duration.Companion.minutes

/** Pure rules of the Spots tab: what it shows for its inputs, and how the rider's changes sit on top of the cache. */
internal object SpotsStateMapper {
    /** The steps the drive changes in. */
    const val STEP_MINUTES = 15

    /** The longest drive the plus button reaches: a day trip. A longer one followed elsewhere can still step down. */
    const val MAX_STEPPED_MINUTES = 720

    /** The longest drive the API takes. */
    const val MAX_MINUTES = 1440

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

    /** The values [spotId] shows: the rider's unsaved edit, or as cached; null for a spot that is not followed. */
    fun editOf(
        inputs: SpotsInputs,
        spotId: SpotId,
    ): SpotEdit? =
        inputs.edits[spotId] ?: inputs.cached
            ?.value
            ?.firstOrNull { it.spot.id == spotId }
            ?.let(::editOf)

    /** One step longer than [minutes], up to [MAX_STEPPED_MINUTES]. */
    fun longer(minutes: Int): Int = (minutes + STEP_MINUTES).coerceAtMost(maxOf(minutes, MAX_STEPPED_MINUTES))

    /** One step shorter than [minutes], down to zero. */
    fun shorter(minutes: Int): Int = (minutes - STEP_MINUTES).coerceAtLeast(0)

    private fun editOf(spot: MySpot): SpotEdit = SpotEdit(driveMinutes = spot.drive.inWholeMinutes.toInt(), favorite = spot.favorite)

    private fun spotUi(
        spot: MySpot,
        edit: SpotEdit?,
    ): MySpotUi {
        val shown = edit ?: editOf(spot)
        return MySpotUi(
            id = spot.spot.id,
            name = spot.spot.name,
            verified = spot.spot.verified,
            favorite = shown.favorite,
            drive = shown.driveMinutes.minutes,
            canShorten = shown.driveMinutes > 0,
            canLengthen = shown.driveMinutes < MAX_STEPPED_MINUTES,
            saving = edit != null,
        )
    }

    private fun notice(error: AppError): Notice = if (error == AppError.Network) Notice.OFFLINE else Notice.REFRESH_FAILED
}
