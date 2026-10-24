package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotChangeEntity
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration

/**
 * The MVI state of the spot picker: the catalog with the rider's choices on top.
 *
 * @property notice why the list may not be current, shown above it.
 * @property changes what "Done" saves, in catalog order: every spot whose choice differs from how it is followed.
 * @property saveError why the last save failed; cleared when the next one starts.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class SpotPickerState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val notice: Notice? = null,
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

        /** The catalog by name. */
        data class Ready(
            val spots: List<PickSpotUi>,
        ) : Content
    }

    enum class Notice {
        /** The last refresh failed because the device is offline. */
        OFFLINE,

        /** The last refresh failed for another reason. */
        REFRESH_FAILED,
    }
}

/** A spot of the catalog as the picker shows it; [drive] can change in steps while [canShorten] or [canLengthen]. */
internal data class PickSpotUi(
    val id: SpotId,
    val name: String,
    val verified: Boolean,
    val selected: Boolean,
    val drive: Duration,
    val canShorten: Boolean,
    val canLengthen: Boolean,
)
