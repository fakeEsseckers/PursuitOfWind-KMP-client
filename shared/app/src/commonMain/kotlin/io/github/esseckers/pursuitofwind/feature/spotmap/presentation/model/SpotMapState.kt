package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model

import io.github.esseckers.pursuitofwind.core.map.SpotMapUi
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.ui.Compass
import io.github.esseckers.pursuitofwind.core.ui.StepperUi
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import org.jetbrains.compose.resources.StringResource

/**
 * The MVI state of the map of spots: what the map draws and the panel under it.
 *
 * @property map null until the spots arrive, so the map opens where it should.
 * @property notice why the spots on the map may not be current.
 * @property baseMapFailed the map could not load, most likely without a connection; it says so, with a retry.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class SpotMapState(
    val map: SpotMapUi? = null,
    val panel: SpotMapPanel = SpotMapPanel.Hint,
    val notice: RefreshNotice? = null,
    val baseMapFailed: Boolean = false,
    val inputs: SpotMapInputs = SpotMapInputs(),
)

/** The panel under the map: what the rider does next. */
internal sealed interface SpotMapPanel {
    /** Nothing placed or selected: how to add a spot, or follow one. */
    data object Hint : SpotMapPanel

    /** The pin is [meters] from [name]: is it that spot, or a new one after all? */
    data class Nearby(
        val spotId: SpotId,
        val name: String,
        val meters: Int,
    ) : SpotMapPanel

    /**
     * A spot being placed or changed. [water] is null until the rider taps the water; [drive] is null when [editing],
     * since the drive belongs to following a spot, not to the spot. [canSave] once there is water and a name;
     * [cancellable] unless the map was opened to change this spot.
     */
    data class Draft(
        val name: String,
        val nameVersion: Int,
        val water: Compass?,
        val drive: StepperUi?,
        val editing: Boolean,
        val cancellable: Boolean,
        val canSave: Boolean,
        val saving: Boolean,
        val error: StringResource?,
    ) : SpotMapPanel

    /** The spot whose marker the rider tapped: follow it as a favorite, unless it is one ([followed]), or change it when [mine]. */
    data class Selected(
        val name: String,
        val followed: Boolean,
        val mine: Boolean,
        val drive: StepperUi,
        val saving: Boolean,
        val error: StringResource?,
    ) : SpotMapPanel
}
