package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_invalid
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrewReducerTest {
    private val reducer = CrewReducer(zone)

    private fun reduce(
        vararg results: CrewResult,
        from: CrewState = CrewPreviewData.ready,
    ): CrewState = results.fold(from, reducer::reduce)

    @Test
    fun `a dialog that is sending stays open when the rider closes it and closes when its request ends`() {
        val sending = reduce(CrewResult.DialogShown(CrewDialog.Join()), CrewResult.DialogSending)
        val dismissed = reduce(CrewResult.DialogDismissed, from = sending)
        val done = reduce(CrewResult.DialogShown(null), from = sending)
        val idle = reduce(CrewResult.DialogShown(CrewDialog.Create()), CrewResult.DialogDismissed)

        assertEquals(CrewDialog.Join(sending = true), sending.dialog)
        assertEquals(sending.dialog, dismissed.dialog)
        assertNull(done.dialog)
        assertNull(idle.dialog)
    }

    @Test
    fun `a failed request keeps the dialog open with the reason and a new try clears it`() {
        val failed =
            reduce(
                CrewResult.DialogShown(CrewDialog.Join()),
                CrewResult.DialogSending,
                CrewResult.DialogFailed(Res.string.crew_invite_invalid),
            )
        val retrying = reduce(CrewResult.DialogSending, from = failed)

        assertEquals(CrewDialog.Join(sending = false, error = Res.string.crew_invite_invalid), failed.dialog)
        assertEquals(CrewDialog.Join(sending = true, error = null), retrying.dialog)
    }

    @Test
    fun `a ride being cancelled shows as pending until the request ends`() {
        val cancelling = reduce(CrewResult.CancelStarted("rid_alex"))
        val done = reduce(CrewResult.CancelFinished("rid_alex"), from = cancelling)

        assertTrue(cancelling.inputs.cancelling.contains("rid_alex"))
        assertTrue(done.inputs.cancelling.isEmpty())
    }

    @Test
    fun `equal content keeps its instance so unchanged cards skip recomposition`() {
        val refreshing = reduce(CrewResult.RefreshStarted)
        val ticked = reduce(CrewResult.Ticked(CrewPreviewData.now))

        assertSame(CrewPreviewData.ready.content, refreshing.content)
        assertSame(CrewPreviewData.ready.content, ticked.content)
        assertTrue(refreshing.isRefreshing)
    }

    @Test
    fun `choosing another crew shows it once its details arrive`() {
        val chosen = reduce(CrewResult.Chosen(CrewPreviewData.nightOwls.id))
        val loaded = reduce(CrewResult.DetailsChanged(CrewPreviewData.details.copy(crewId = CrewPreviewData.nightOwls.id)), from = chosen)

        assertEquals(CrewState.Content.Loading, chosen.content)
        assertEquals("Night Owls", (loaded.content as CrewState.Content.Ready).crew.name)
        assertEquals(listOf(false, true), loaded.crews.map { it.selected })
    }

    @Test
    fun `the chosen crew shows while it is one of the crews or else the first`() {
        val owls = CrewPreviewData.nightOwls.id
        val chosen = reduce(CrewResult.Chosen(owls))
        val left = reduce(CrewResult.CrewsChanged(crews(CrewPreviewData.weekendCrew)), from = chosen)
        val noCrew = reduce(CrewResult.CrewsChanged(crews()), from = chosen)
        val noChoice = reduce(CrewResult.CrewsChanged(crews(CrewPreviewData.weekendCrew, CrewPreviewData.nightOwls)), from = CrewState())

        assertEquals(owls, chosen.inputs.shown)
        assertEquals(CrewPreviewData.weekendCrew.id, left.inputs.shown)
        assertNull(noCrew.inputs.shown)
        assertEquals(CrewPreviewData.weekendCrew.id, noChoice.inputs.shown)
    }

    @Test
    fun `a crew just made shows before the list holds it and the list decides once its first refresh is done`() {
        val arrived = reduce(CrewResult.Arrived("crw_new"))
        val listedBefore = reduce(CrewResult.CrewsChanged(crews(CrewPreviewData.weekendCrew, CrewPreviewData.nightOwls)), from = arrived)
        val settled = reduce(CrewResult.ArrivalSettled("crw_new"), from = listedBefore)

        assertEquals("crw_new", arrived.inputs.shown)
        assertEquals("crw_new", listedBefore.inputs.shown)
        // It never came into the list: the rider's choice is gone, so the first crew shows.
        assertNull(settled.inputs.arriving)
        assertEquals(CrewPreviewData.weekendCrew.id, settled.inputs.shown)
    }

    private fun crews(vararg crews: Crew): Cached<List<Crew>> = Cached(crews.toList(), CrewPreviewData.now)
}
