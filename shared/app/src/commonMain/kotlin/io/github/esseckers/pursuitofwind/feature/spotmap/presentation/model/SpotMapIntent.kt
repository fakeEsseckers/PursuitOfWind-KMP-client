package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model

import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/** What the rider can ask of the map of spots. */
internal sealed interface SpotMapIntent {
    /** A long press on the map: put a new spot's pin here, or move the pin of the spot being placed. */
    data class LongPress(
        val point: GeoPoint,
    ) : SpotMapIntent

    /** A tap on the map: with a pin placed, the water lies this way from it; without one, nothing is selected any more. */
    data class Tap(
        val point: GeoPoint,
    ) : SpotMapIntent

    /** A tap on [spotId]'s marker: select it, unless a spot is being placed. */
    data class SelectSpot(
        val spotId: SpotId,
    ) : SpotMapIntent

    /** The name typed for the spot being placed. */
    data class Rename(
        val name: String,
    ) : SpotMapIntent

    /** Moves the drive to the spot one [step] longer or shorter. */
    data class ChangeDrive(
        val step: Step,
    ) : SpotMapIntent

    /** A spot already near the pin is not the one: add a new spot here after all. */
    data object AddHere : SpotMapIntent

    /** The spot near the pin is the one: select it instead of adding a new one. */
    data object UseNearby : SpotMapIntent

    /** Follow the selected spot. */
    data object Follow : SpotMapIntent

    /** Change the selected spot, which the rider added. */
    data object EditSelected : SpotMapIntent

    /** Save the spot being placed or changed. */
    data object Save : SpotMapIntent

    /** Drop the pin or the selection and start over; a change started on the map goes back to its spot. */
    data object Cancel : SpotMapIntent

    /** Fetch the spots again, or retry after an error; also loads the base map again if it did not load. */
    data object Refresh : SpotMapIntent

    /** The base map loaded ([loaded]), or could not load, most likely without a connection. */
    data class BaseMap(
        val loaded: Boolean,
    ) : SpotMapIntent

    /** Load the base map again. */
    data object RetryBaseMap : SpotMapIntent
}
