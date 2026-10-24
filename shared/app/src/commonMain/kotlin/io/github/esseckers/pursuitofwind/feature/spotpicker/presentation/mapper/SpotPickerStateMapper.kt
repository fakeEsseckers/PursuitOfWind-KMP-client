package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.model.SpotChange
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.DriveLimits
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickSpotUi
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotChoice
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerInputs
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Content
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Notice
import kotlin.time.Duration.Companion.minutes

/** Pure rules of the spot picker: what it shows for its inputs, including what "Done" saves. */
internal object SpotPickerStateMapper {
    fun map(inputs: SpotPickerInputs): SpotPickerState {
        val refresh = inputs.refresh
        val base =
            SpotPickerState(
                isRefreshing = refresh.refreshing,
                saving = inputs.saving,
                saveError = inputs.saveError?.message(),
                inputs = inputs,
            )
        val snapshot = inputs.snapshot ?: return base
        val spots = snapshot.catalog.value.map { spotUi(it, choiceOf(inputs, it.id)) }
        val error = refresh.error.takeUnless { refresh.refreshing }
        val content =
            when {
                spots.isNotEmpty() -> Content.Ready(spots)
                error != null -> Content.Error(error.message())
                refresh.finished && !refresh.refreshing -> Content.Empty
                else -> Content.Loading
            }
        return base.copy(
            content = content,
            // Cached spots win over an error: the list stays, with a notice above it.
            notice = if (content is Content.Ready) error?.let(::notice) else null,
            selectedCount = spots.count { it.selected },
            changes = changes(inputs),
        )
    }

    /** The rider's choice for [spotId]: their edit, else how it is followed, else not chosen with the default drive. */
    fun choiceOf(
        inputs: SpotPickerInputs,
        spotId: SpotId,
    ): SpotChoice {
        inputs.choices[spotId]?.let { return it }
        val mine = inputs.snapshot?.mySpots?.firstOrNull { it.spot.id == spotId }
        return if (mine != null) {
            SpotChoice(selected = mine.favorite, driveMinutes = mine.drive.inWholeMinutes.toInt())
        } else {
            SpotChoice(selected = false, driveMinutes = DriveLimits.DEFAULT_MINUTES)
        }
    }

    /**
     * What "Done" saves, in catalog order: every spot whose choice differs from how it is followed. A spot that is not
     * followed and not chosen stays as it is, whatever its drive time says.
     */
    private fun changes(inputs: SpotPickerInputs): List<SpotChange> {
        val snapshot = inputs.snapshot ?: return emptyList()
        val followed = snapshot.mySpots.associateBy { it.spot.id }
        return snapshot.catalog.value.mapNotNull { spot ->
            val choice = inputs.choices[spot.id] ?: return@mapNotNull null
            val mine = followed[spot.id]
            val unchanged = mine != null && mine.favorite == choice.selected && mine.drive == choice.driveMinutes.minutes
            if (unchanged || (mine == null && !choice.selected)) null else SpotChange(spot.id, choice.driveMinutes.minutes, choice.selected)
        }
    }

    private fun spotUi(
        spot: Spot,
        choice: SpotChoice,
    ): PickSpotUi =
        PickSpotUi(
            id = spot.id,
            name = spot.name,
            verified = spot.verified,
            selected = choice.selected,
            drive = choice.driveMinutes.minutes,
            canShorten = choice.driveMinutes > 0,
            canLengthen = choice.driveMinutes < DriveLimits.MAX_MINUTES,
        )

    private fun notice(error: AppError): Notice = if (error == AppError.Network) Notice.OFFLINE else Notice.REFRESH_FAILED
}
