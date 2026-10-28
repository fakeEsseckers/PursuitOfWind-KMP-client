package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.mapper

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNoticeMapper
import io.github.esseckers.pursuitofwind.core.ui.DEFAULT_SPOT_DRIVE_MINUTES
import io.github.esseckers.pursuitofwind.core.ui.SPOT_DRIVE_STEPS
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotChangeEntity
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickSpotUi
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotChoice
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerInputs
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Content
import kotlin.time.Duration.Companion.minutes

/** Pure rules of the spot picker: what it shows for its inputs, including what "Done" saves. Newest spots come first. */
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
        val spots = newestFirst(snapshot.catalog.value).map { spotUi(it, choiceOf(inputs, it.id)) }
        val error = refresh.shownError
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
            notice = if (content is Content.Ready) RefreshNoticeMapper.notice(error) else null,
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
            SpotChoice(selected = false, driveMinutes = DEFAULT_SPOT_DRIVE_MINUTES)
        }
    }

    /**
     * What "Done" saves, in the order the picker lists the spots: every spot whose choice differs from how it is
     * followed. A spot that is not followed and not chosen stays as it is, whatever its drive time says.
     */
    private fun changes(inputs: SpotPickerInputs): List<SpotChangeEntity> {
        val snapshot = inputs.snapshot ?: return emptyList()
        val followed = snapshot.mySpots.associateBy { it.spot.id }
        return newestFirst(snapshot.catalog.value).mapNotNull { spot ->
            val choice = inputs.choices[spot.id] ?: return@mapNotNull null
            val mine = followed[spot.id]
            val unchanged = mine != null && mine.favorite == choice.selected && mine.drive == choice.driveMinutes.minutes
            if (unchanged ||
                (mine == null && !choice.selected)
            ) {
                null
            } else {
                SpotChangeEntity(spot.id, choice.driveMinutes.minutes, choice.selected)
            }
        }
    }

    /** The spots riders added last come first, so a spot just added on the map is at the top; a tie goes by name. */
    private fun newestFirst(catalog: List<Spot>): List<Spot> =
        catalog.sortedWith(compareByDescending<Spot> { it.createdAt }.thenBy { it.name })

    private fun spotUi(
        spot: Spot,
        choice: SpotChoice,
    ): PickSpotUi {
        val drive = SPOT_DRIVE_STEPS.stepper(choice.driveMinutes)
        return PickSpotUi(
            id = spot.id,
            name = spot.name,
            selected = choice.selected,
            drive = choice.driveMinutes.minutes,
            canShorten = drive.canDecrease,
            canLengthen = drive.canIncrease,
        )
    }
}
