package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity

/** What happened on the map of spots, for [SpotMapReducer]. */
internal sealed interface SpotMapResult {
    data class DataChanged(
        val snapshot: SpotMapSnapshotEntity,
    ) : SpotMapResult

    data object RefreshStarted : SpotMapResult

    data class RefreshFinished(
        val error: AppError?,
    ) : SpotMapResult

    /** The base map under the spots: whether it loaded, and another attempt to load it. */
    sealed interface BaseMap : SpotMapResult {
        data class Loaded(
            val loaded: Boolean,
        ) : BaseMap

        data object Retried : BaseMap
    }

    /** The rider places a spot: the pin, its water, its name, its drive. */
    sealed interface Placing : SpotMapResult {
        data class LongPressed(
            val point: GeoPoint,
        ) : Placing

        data class Tapped(
            val point: GeoPoint,
        ) : Placing

        data class Renamed(
            val name: String,
        ) : Placing

        data class DriveChanged(
            val step: Step,
        ) : Placing

        data object AddedHere : Placing

        data object Cancelled : Placing
    }

    /** The rider picks a spot of the catalog: to follow it, or to change it. */
    sealed interface Choosing : SpotMapResult {
        data class Selected(
            val spotId: SpotId,
        ) : Choosing

        data object UsedNearby : Choosing

        data object EditStarted : Choosing
    }

    /**
     * A save or a follow: started, a new spot that is in the catalog but could not be followed ([Added], before its
     * [Failed]), or failed. Success closes the map.
     */
    sealed interface Saving : SpotMapResult {
        data object Started : Saving

        data class Added(
            val spotId: SpotId,
        ) : Saving

        data class Failed(
            val error: AppError,
        ) : Saving
    }
}
