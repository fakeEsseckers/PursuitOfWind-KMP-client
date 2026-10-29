package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

import io.github.esseckers.pursuitofwind.core.map.SpotMapUi
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotChangeEntity
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration

/**
 * The MVI state of the spot picker: the catalog with the rider's choices on top.
 *
 * @property notice why the list may not be current, shown above it.
 * @property view whether the catalog shows as a list or on the map.
 * @property map the catalog on the map, once there is a catalog: the chosen spots stand out, the [mapSpot] most.
 * @property mapSpot the spot picked on the map, shown under it as in the list; null until one is picked.
 * @property baseMapFailed the map could not load, most likely without a connection; it says so, with a retry.
 * @property changes what "Done" saves, in the order of the list: every spot whose choice differs from how it is followed.
 * @property saveError why the last save failed; cleared when the next one starts.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class SpotPickerState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val notice: RefreshNotice? = null,
    val view: PickerView = PickerView.LIST,
    val map: SpotMapUi? = null,
    val mapSpot: PickSpotUi? = null,
    val baseMapFailed: Boolean = false,
    val selectedCount: Int = 0,
    val changes: List<SpotChangeEntity> = emptyList(),
    val saving: Boolean = false,
    val saveError: StringResource? = null,
    val inputs: SpotPickerInputs = SpotPickerInputs(),
) {
    /** Whether "Done" would save anything. */
    val hasChanges: Boolean get() = changes.isNotEmpty()

    sealed interface Content {
        data object Loading : Content

        /** The catalog has no spots. */
        data object Empty : Content

        /** Nothing cached to show, and the refresh failed. */
        data class Error(
            val message: StringResource,
        ) : Content

        /** The catalog, the spots added last first. */
        data class Ready(
            val spots: List<PickSpotUi>,
        ) : Content
    }
}

/** How the catalog shows: as a list of names, or on the map, where it is clear where each spot is. */
internal enum class PickerView {
    LIST,
    MAP,
}

/** A spot of the catalog as the picker shows it; [drive] can change in steps while [canShorten] or [canLengthen]. */
internal data class PickSpotUi(
    val id: SpotId,
    val name: String,
    val selected: Boolean,
    val drive: Duration,
    val canShorten: Boolean,
    val canLengthen: Boolean,
)
