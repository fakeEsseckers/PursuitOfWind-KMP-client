package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.mapper

import io.github.esseckers.pursuitofwind.core.domain.mapper.GeoMapper
import io.github.esseckers.pursuitofwind.core.map.MapCameraUi
import io.github.esseckers.pursuitofwind.core.map.SpotMapUi
import io.github.esseckers.pursuitofwind.core.map.SpotMarkerUi
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNoticeMapper
import io.github.esseckers.pursuitofwind.core.ui.Compass
import io.github.esseckers.pursuitofwind.core.ui.SPOT_DRIVE_STEPS
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotDraft
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapInputs
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapPanel
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import kotlin.math.roundToInt

/** Computes the map of spots from its inputs. Called by the reducer only. */
internal object SpotMapStateMapper {
    /** A spot this close to a new pin is offered instead of adding another one at the same beach. */
    const val NEARBY_METERS: Double = 1_000.0

    /** A tap this close to the pin is too close to tell which way the water lies. */
    const val MIN_WATER_METERS: Double = 5.0

    /** Distances to a nearby spot are rounded to tens of metres. */
    private const val ROUND_METERS = 10

    fun map(inputs: SpotMapInputs): SpotMapState {
        val notice = RefreshNoticeMapper.notice(inputs.refresh.shownError)
        val snapshot = inputs.snapshot ?: return SpotMapState(notice = notice, baseMapFailed = inputs.baseMapFailed, inputs = inputs)
        val followed = favorites(snapshot)
        val draft = inputs.draft
        val markers =
            snapshot.catalog.value
                // The spot being changed shows as the pin.
                .filter { it.id != inputs.editing }
                .map { SpotMarkerUi(it.id, it.name, it.location, followed = it.id in followed, selected = it.id == inputs.selected) }
        val map =
            SpotMapUi(
                markers = markers,
                pin = draft?.pin,
                water = draft?.water,
                sectors = draft?.water?.let(DecisionEngine::sectorsFacing),
                start = start(inputs, snapshot),
                attempt = inputs.baseMapAttempt,
            )
        return SpotMapState(
            map = map,
            panel = panel(inputs, snapshot),
            notice = notice,
            baseMapFailed = inputs.baseMapFailed,
            inputs = inputs,
        )
    }

    /** The spot nearest to the pin of a new spot, if one is close enough to be the same beach; null once dismissed. */
    fun nearby(inputs: SpotMapInputs): Spot? {
        val pin = inputs.draft?.pin
        if (pin == null || inputs.editing != null || inputs.nearbyDismissed) return null
        return inputs.snapshot
            ?.catalog
            ?.value
            .orEmpty()
            .map { it to GeoMapper.distanceMeters(pin, it.location) }
            .filter { (_, meters) -> meters <= NEARBY_METERS }
            .minByOrNull { (_, meters) -> meters }
            ?.first
    }

    /** The spot the rider changes or tapped: from the catalog, or from their own spots while the catalog is not cached. */
    fun spot(
        inputs: SpotMapInputs,
        id: SpotId?,
    ): Spot? {
        val snapshot = inputs.snapshot
        if (id == null || snapshot == null) return null
        return snapshot.catalog.value.firstOrNull { it.id == id } ?: snapshot.mySpots.firstOrNull { it.spot.id == id }?.spot
    }

    /** The rider's favorites: the spots they get an answer for. One followed but not a favorite can be followed again. */
    private fun favorites(snapshot: SpotMapSnapshotEntity): Set<SpotId> =
        snapshot.mySpots
            .filter { it.favorite }
            .map { it.spot.id }
            .toSet()

    /** The draft to change [spot] from: it as it is now. */
    fun draftOf(
        spot: Spot,
        version: Int,
    ): SpotDraft = SpotDraft(pin = spot.location, water = spot.water, name = spot.name, nameVersion = version)

    /** Where the map opens: at the spot being changed, over the first of the rider's favorites by name, or the whole world. */
    private fun start(
        inputs: SpotMapInputs,
        snapshot: SpotMapSnapshotEntity,
    ): MapCameraUi {
        val editing = spot(inputs, inputs.editing)
        val firstFollowed =
            snapshot.mySpots
                .filter { it.favorite }
                .minByOrNull { it.spot.name }
                ?.spot
        return when {
            editing != null -> MapCameraUi(editing.location, MapCameraUi.SPOT_ZOOM)
            firstFollowed != null -> MapCameraUi(firstFollowed.location, MapCameraUi.REGION_ZOOM)
            else -> MapCameraUi.WORLD
        }
    }

    private fun panel(
        inputs: SpotMapInputs,
        snapshot: SpotMapSnapshotEntity,
    ): SpotMapPanel {
        val draft = inputs.draft
        val selected = spot(inputs, inputs.selected)
        val nearby = nearby(inputs)
        return when {
            draft != null && nearby != null -> {
                SpotMapPanel.Nearby(nearby.id, nearby.name, roundedMeters(GeoMapper.distanceMeters(draft.pin, nearby.location)))
            }

            draft != null -> {
                draftPanel(inputs, draft)
            }

            selected != null -> {
                SpotMapPanel.Selected(
                    name = selected.name,
                    followed = selected.id in favorites(snapshot),
                    mine = snapshot.userId != null && selected.createdBy == snapshot.userId,
                    drive = SPOT_DRIVE_STEPS.stepper(inputs.driveMinutes),
                    saving = inputs.saving,
                    error = inputs.saveError?.message(),
                )
            }

            else -> {
                SpotMapPanel.Hint
            }
        }
    }

    private fun draftPanel(
        inputs: SpotMapInputs,
        draft: SpotDraft,
    ): SpotMapPanel.Draft =
        SpotMapPanel.Draft(
            name = draft.name,
            nameVersion = draft.nameVersion,
            water = draft.water?.let { Compass.of(it.degrees) },
            drive = if (inputs.editing == null) SPOT_DRIVE_STEPS.stepper(inputs.driveMinutes) else null,
            editing = inputs.editing != null,
            cancellable = !inputs.editOnly,
            canSave = draft.water != null && draft.name.isNotBlank() && !inputs.saving,
            saving = inputs.saving,
            error = inputs.saveError?.message(),
        )

    private fun roundedMeters(meters: Double): Int = (meters / ROUND_METERS).roundToInt() * ROUND_METERS
}
