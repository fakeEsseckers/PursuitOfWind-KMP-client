package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration

/**
 * The MVI state of the Spots tab: the followed spots with the rider's unsaved changes on top.
 *
 * @property notice why the list may not be current, shown above it.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class SpotsState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val notice: Notice? = null,
    val inputs: SpotsInputs = SpotsInputs(),
) {
    sealed interface Content {
        data object Loading : Content

        /** The rider follows no spot. */
        data object Empty : Content

        /** Nothing cached to show, and the refresh failed. */
        data class Error(
            val message: StringResource,
        ) : Content

        /** The followed spots by name. */
        data class Ready(
            val spots: List<MySpotUi>,
        ) : Content
    }

    enum class Notice {
        /** The last refresh failed because the device is offline. */
        OFFLINE,

        /** The last refresh failed for another reason. */
        REFRESH_FAILED,
    }
}

/**
 * A followed spot as the tab shows it. [saving] is true while a change the rider made has not reached the cache yet,
 * [restoring] while the spot comes back after "Undo"; [drive] changes in steps while [canShorten] or [canLengthen].
 */
internal data class MySpotUi(
    val id: SpotId,
    val name: String,
    val verified: Boolean,
    val favorite: Boolean,
    val drive: Duration,
    val canShorten: Boolean,
    val canLengthen: Boolean,
    val saving: Boolean,
    val restoring: Boolean,
)
