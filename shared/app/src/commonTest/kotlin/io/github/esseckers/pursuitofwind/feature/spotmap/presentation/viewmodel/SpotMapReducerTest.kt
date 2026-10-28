package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.map.MapCameraUi
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.Compass
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.StepperUi
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapPanel
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.RIDER
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.beach
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.dune
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.nordstrand
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.saltLagoon
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.westOfBeach
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The map of spots' rules: what a press, a tap and a pick do, and what the map and its panel show for them. */
class SpotMapReducerTest {
    private fun reduce(
        from: SpotMapState,
        vararg results: SpotMapResult,
    ): SpotMapState = results.fold(from, SpotMapReducer::reduce)

    private fun SpotMapState.draft(): SpotMapPanel.Draft = assertIs<SpotMapPanel.Draft>(panel)

    private fun SpotMapState.selected(): SpotMapPanel.Selected = assertIs<SpotMapPanel.Selected>(panel)

    @Test
    fun `the catalog shows as markers with the followed spots standing out around the rider's spots`() {
        val map = SpotMapPreviewData.browsing.map!!

        assertEquals(listOf("dune-point", "nordstrand-bay", "salt-lagoon"), map.markers.map { it.id.value })
        assertEquals(listOf("dune-point", "nordstrand-bay"), map.markers.filter { it.followed }.map { it.id.value })
        assertTrue(map.markers.none { it.selected })
        // The first of the rider's spots by name.
        assertEquals(MapCameraUi(dune.location, MapCameraUi.REGION_ZOOM), map.start)
        assertEquals(SpotMapPanel.Hint, SpotMapPreviewData.browsing.panel)
    }

    @Test
    fun `the map waits for the spots and opens over the world for a rider without any`() {
        val noSpots =
            reduce(
                SpotMapState(),
                SpotMapResult.DataChanged(SpotMapSnapshotEntity(Cached(listOf(saltLagoon), FETCHED_AT), emptyList(), RIDER)),
            )

        assertNull(SpotMapPreviewData.loading.map)
        assertEquals(MapCameraUi.WORLD, noSpots.map?.start)
    }

    @Test
    fun `a long press away from every spot places a pin that waits for the water`() {
        val pinned = SpotMapPreviewData.pinned

        assertEquals(beach, pinned.map?.pin)
        assertNull(pinned.map?.water)
        assertNull(pinned.map?.sectors)
        val draft = pinned.draft()
        assertNull(draft.water)
        assertEquals(StepperUi(value = 60, canDecrease = true, canIncrease = true), draft.drive)
        assertFalse(draft.editing)
        assertFalse(draft.canSave)
    }

    @Test
    fun `a tap on the water turns the arrow towards it and draws the spot's sectors`() {
        val placed = SpotMapPreviewData.placed
        val map = placed.map!!
        val water = map.water!!

        assertTrue(abs(water.degrees - 270.0) < 1.0, "water at ${water.degrees}°")
        assertEquals(DecisionEngine.sectorsFacing(water), map.sectors)
        assertEquals(Compass.W, placed.draft().water)
        assertTrue(placed.draft().canSave)
    }

    @Test
    fun `a tap next to the pin does not say where the water is`() {
        val tooClose = reduce(SpotMapPreviewData.pinned, SpotMapResult.Placing.Tapped(GeoPoint(beach.latitude + 0.00002, beach.longitude)))

        assertNull(tooClose.draft().water)
    }

    @Test
    fun `a spot needs a name to be saved`() {
        val blank = reduce(SpotMapPreviewData.placed, SpotMapResult.Placing.Renamed("  "))

        assertFalse(blank.draft().canSave)
    }

    @Test
    fun `a long press moves the pin and keeps the water and the name`() {
        val moved = GeoPoint(54.41, 8.60)
        val state = reduce(SpotMapPreviewData.placed, SpotMapResult.Placing.LongPressed(moved))

        assertEquals(moved, state.map?.pin)
        assertEquals(SpotMapPreviewData.placed.map?.water, state.map?.water)
        assertEquals("Sand Spit", state.draft().name)
    }

    @Test
    fun `the drive to a new spot steps a quarter hour`() {
        val longer = reduce(SpotMapPreviewData.pinned, SpotMapResult.Placing.DriveChanged(Step.UP))

        assertEquals(75, longer.draft().drive?.value)
    }

    @Test
    fun `a pin next to a spot offers that spot before anything else`() {
        val nearby = SpotMapPreviewData.nearby
        val tapped = reduce(nearby, SpotMapResult.Placing.Tapped(GeoPoint(54.5027, 8.79)))

        assertEquals(SpotMapPanel.Nearby(nordstrand.id, "Nordstrand Bay", meters = 300), nearby.panel)
        assertNull(tapped.map?.water)
    }

    @Test
    fun `a new spot can still be added next to one`() {
        val here = reduce(SpotMapPreviewData.nearby, SpotMapResult.Placing.AddedHere)
        val turned = reduce(here, SpotMapResult.Placing.Tapped(GeoPoint(54.5027, 8.79)))

        assertNull(here.draft().water)
        assertEquals(Compass.W, turned.draft().water)
    }

    @Test
    fun `the spot near the pin can be taken instead`() {
        val used = reduce(SpotMapPreviewData.nearby, SpotMapResult.Choosing.UsedNearby)

        assertNull(used.map?.pin)
        assertEquals("Nordstrand Bay", used.selected().name)
        assertTrue(used.selected().followed)
        assertTrue(
            used.map!!
                .markers
                .single { it.selected }
                .id == nordstrand.id,
        )
    }

    @Test
    fun `a tapped spot can be followed and the rider's own one changed`() {
        val other = SpotMapPreviewData.selected.selected()
        val mine = SpotMapPreviewData.selectedMine.selected()

        assertEquals("Salt Lagoon", other.name)
        assertFalse(other.followed)
        assertFalse(other.mine)
        assertEquals(60, other.drive.value)
        assertTrue(mine.followed)
        assertTrue(mine.mine)
    }

    @Test
    fun `someone else's spot cannot be changed`() {
        val state = reduce(SpotMapPreviewData.selected, SpotMapResult.Choosing.EditStarted)

        assertEquals(SpotMapPreviewData.selected.panel, state.panel)
        assertNull(state.inputs.editing)
    }

    @Test
    fun `changing the rider's own spot starts from it as it is`() {
        val state = reduce(SpotMapPreviewData.selectedMine, SpotMapResult.Choosing.EditStarted)
        val draft = state.draft()

        assertEquals(dune.id, state.inputs.editing)
        assertEquals(dune.location, state.map?.pin)
        // The spot shows as the pin, not as a marker under it.
        assertTrue(state.map!!.markers.none { it.id == dune.id })
        assertEquals("Dune Point", draft.name)
        assertEquals(Compass.NW, draft.water)
        assertNull(draft.drive)
        assertTrue(draft.editing)
        assertTrue(draft.canSave)
    }

    @Test
    fun `a spot opened to be changed stays the one on the map`() {
        val editing = SpotMapPreviewData.editing
        val tappedAnother = reduce(editing, SpotMapResult.Choosing.Selected(saltLagoon.id))

        assertEquals("Dune Point", editing.draft().name)
        assertEquals(MapCameraUi(dune.location, MapCameraUi.SPOT_ZOOM), editing.map?.start)
        assertEquals(editing.panel, tappedAnother.panel)
    }

    @Test
    fun `the spot holds still while it is being saved but its name and drive can still change`() {
        val saving = reduce(SpotMapPreviewData.placed, SpotMapResult.Saving.Started)
        val pressed =
            reduce(
                saving,
                SpotMapResult.Placing.LongPressed(GeoPoint(54.41, 8.60)),
                SpotMapResult.Placing.Tapped(GeoPoint(54.40, 8.61)),
                SpotMapResult.Choosing.Selected(saltLagoon.id),
                SpotMapResult.Placing.Cancelled,
            )
        val typed = reduce(saving, SpotMapResult.Placing.Renamed("Sand Spit North"), SpotMapResult.Placing.DriveChanged(Step.UP))
        val retry = reduce(typed, SpotMapResult.Saving.Failed(AppError.Network))

        assertTrue(saving.draft().saving)
        assertFalse(saving.draft().canSave)
        assertEquals(saving, pressed)
        assertEquals("Sand Spit North", retry.draft().name)
        assertEquals(75, retry.draft().drive?.value)
        assertTrue(retry.draft().canSave)
    }

    @Test
    fun `a tap on a marker keeps the spot being placed`() {
        val tapped = reduce(SpotMapPreviewData.placed, SpotMapResult.Choosing.Selected(saltLagoon.id))

        assertEquals(SpotMapPreviewData.placed, tapped)
    }

    @Test
    fun `typing a name keeps the map as it was`() {
        val renamed = reduce(SpotMapPreviewData.placed, SpotMapResult.Placing.Renamed("Sand Spit North"))

        assertSame(SpotMapPreviewData.placed.map, renamed.map)
    }

    @Test
    fun `a spot followed but not as a favorite can be followed again at its drive`() {
        val snapshot =
            SpotMapSnapshotEntity(
                Cached(listOf(dune, nordstrand, saltLagoon), FETCHED_AT),
                listOf(MySpot(saltLagoon, 90.minutes, favorite = false)),
                RIDER,
            )
        val state = reduce(SpotMapState(), SpotMapResult.DataChanged(snapshot), SpotMapResult.Choosing.Selected(saltLagoon.id))

        assertFalse(state.selected().followed)
        assertEquals(90, state.selected().drive.value)
        val map = state.map!!
        assertTrue(map.markers.none { it.followed })
        assertEquals(MapCameraUi.WORLD, map.start)
    }

    @Test
    fun `cancel on a change started on the map goes back to the spot`() {
        val editing = reduce(SpotMapPreviewData.selectedMine, SpotMapResult.Choosing.EditStarted)
        val cancelled = reduce(editing, SpotMapResult.Placing.Cancelled)

        assertTrue(editing.draft().cancellable)
        assertNull(cancelled.inputs.editing)
        assertEquals("Dune Point", cancelled.selected().name)
        assertTrue(
            cancelled.map!!
                .markers
                .single { it.selected }
                .id == dune.id,
        )
    }

    @Test
    fun `the map opened to change a spot has no cancel`() {
        val editing = SpotMapPreviewData.editing

        assertFalse(editing.draft().cancellable)
        assertEquals(editing, reduce(editing, SpotMapResult.Placing.Cancelled))
    }

    @Test
    fun `a failed save says why and keeps the spot to try again`() {
        val draft = SpotMapPreviewData.saveFailed.draft()

        assertEquals(Res.string.error_network, draft.error)
        assertFalse(draft.saving)
        assertTrue(draft.canSave)
        assertEquals("Sand Spit", draft.name)
    }

    @Test
    fun `a new spot that was added but not followed becomes the selected spot`() {
        val added = dune.copy(id = SpotId("spt_0001"), name = "Sand Spit", location = beach)
        val catalog = listOf(added, dune, nordstrand, saltLagoon)
        val state =
            reduce(
                SpotMapPreviewData.placed,
                SpotMapResult.Saving.Started,
                SpotMapResult.Saving.Added(added.id),
                SpotMapResult.DataChanged(SpotMapSnapshotEntity(Cached(catalog, FETCHED_AT), emptyList(), RIDER)),
                SpotMapResult.Saving.Failed(AppError.Network),
            )
        val panel = state.selected()

        assertNull(state.map?.pin)
        assertEquals("Sand Spit", panel.name)
        assertFalse(panel.followed)
        assertTrue(panel.mine)
        assertEquals(Res.string.error_network, panel.error)
    }

    @Test
    fun `a base map that did not load says so until another attempt`() {
        val failed = SpotMapPreviewData.offline
        val retried = reduce(failed, SpotMapResult.BaseMap.Retried)
        val loaded = reduce(retried, SpotMapResult.BaseMap.Loaded(loaded = true))

        assertTrue(failed.baseMapFailed)
        assertEquals(0, failed.map?.attempt)
        assertFalse(retried.baseMapFailed)
        assertEquals(1, retried.map?.attempt)
        assertFalse(loaded.baseMapFailed)
        assertTrue(reduce(loaded, SpotMapResult.BaseMap.Loaded(loaded = false)).baseMapFailed)
    }

    @Test
    fun `cancel and a tap beside the spots start over`() {
        val cancelled = reduce(SpotMapPreviewData.placed, SpotMapResult.Placing.Cancelled)
        val letGo = reduce(SpotMapPreviewData.selected, SpotMapResult.Placing.Tapped(westOfBeach))

        assertEquals(SpotMapPanel.Hint, cancelled.panel)
        assertNull(cancelled.map?.pin)
        assertEquals(SpotMapPanel.Hint, letGo.panel)
        assertTrue(letGo.map!!.markers.none { it.selected })
    }

    private companion object {
        val FETCHED_AT: Instant = Instant.parse("2026-10-24T06:00:00Z")
    }
}
