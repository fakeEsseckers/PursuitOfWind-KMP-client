package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_server
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotpicker.following
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Content
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview.SpotPickerPreviewData.dune
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview.SpotPickerPreviewData.nordstrand
import io.github.esseckers.pursuitofwind.feature.spotpicker.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SpotPickerReducerTest {
    private val loaded =
        reduce(SpotPickerResult.DataChanged(snapshot(following(nordstrand, 2.hours))), SpotPickerResult.RefreshFinished(null))

    private fun reduce(
        vararg results: SpotPickerResult,
        from: SpotPickerState = SpotPickerState(),
    ): SpotPickerState = results.fold(from, SpotPickerReducer::reduce)

    private fun SpotPickerState.spot(id: String) = (content as Content.Ready).spots.single { it.id.value == id }

    /** The picker with Nordstrand Bay followed at [drive]. */
    private fun drivenFrom(drive: Duration): SpotPickerState =
        reduce(SpotPickerResult.DataChanged(snapshot(following(nordstrand, drive))), SpotPickerResult.RefreshFinished(null))

    /** Nordstrand Bay's drive after one [step] from this state. */
    private fun SpotPickerState.stepped(step: Step): Duration =
        reduce(SpotPickerResult.DriveChanged(nordstrand.id, step), from = this).spot("nordstrand-bay").drive

    @Test
    fun `a tap chooses a spot and a second one unchooses it`() {
        val chosen = reduce(SpotPickerResult.Toggled(dune.id), from = loaded)
        val unchosen = reduce(SpotPickerResult.Toggled(dune.id), from = chosen)

        assertTrue(chosen.spot("dune-point").selected)
        assertEquals(2, chosen.selectedCount)
        assertTrue(chosen.hasChanges)
        assertFalse(unchosen.spot("dune-point").selected)
        assertFalse(unchosen.hasChanges)
    }

    @Test
    fun `the drive of a followed spot steps a quarter hour from where it was`() {
        val longer = reduce(SpotPickerResult.DriveChanged(nordstrand.id, Step.UP), from = loaded)
        val shorter = reduce(SpotPickerResult.DriveChanged(nordstrand.id, Step.DOWN), from = loaded)

        assertEquals(135.minutes, longer.spot("nordstrand-bay").drive)
        assertTrue(longer.spot("nordstrand-bay").selected)
        assertEquals(105.minutes, shorter.spot("nordstrand-bay").drive)
    }

    @Test
    fun `the drive stops at no drive and at a day trip even off the quarter hour`() {
        assertEquals(0.minutes, drivenFrom(10.minutes).stepped(Step.DOWN))
        assertEquals(12.hours, drivenFrom(710.minutes).stepped(Step.UP))
    }

    @Test
    fun `a drive longer than the plus button reaches steps down from where it is`() {
        val far = drivenFrom(15.hours)

        assertFalse(far.spot("nordstrand-bay").canLengthen)
        assertEquals(885.minutes, far.stepped(Step.DOWN))
        assertEquals(15.hours, far.stepped(Step.UP))
    }

    @Test
    fun `choices stay on top of the cache until it shows them saved`() {
        val chosen = reduce(SpotPickerResult.Toggled(dune.id), from = loaded)
        val saved =
            reduce(
                SpotPickerResult.SaveStarted,
                SpotPickerResult.DataChanged(snapshot(following(nordstrand, 2.hours), following(dune, 1.hours))),
                SpotPickerResult.SaveFinished(null),
                from = chosen,
            )

        assertTrue(saved.spot("dune-point").selected)
        assertFalse(saved.hasChanges)
        assertFalse(saved.saving)
    }

    @Test
    fun `a failed save says why and keeps the choices`() {
        val failed =
            reduce(
                SpotPickerResult.Toggled(dune.id),
                SpotPickerResult.SaveStarted,
                SpotPickerResult.SaveFinished(AppError.Server(503)),
                from = loaded,
            )
        val again = reduce(SpotPickerResult.SaveStarted, from = failed)

        assertEquals(Res.string.error_server, failed.saveError)
        assertTrue(failed.hasChanges)
        assertNull(again.saveError)
        assertTrue(again.saving)
    }

    @Test
    fun `a refresh is remembered as finished so the picker does not fetch again on return`() {
        val started = reduce(SpotPickerResult.RefreshStarted)
        val finished = reduce(SpotPickerResult.RefreshFinished(AppError.Network), from = started)

        assertTrue(started.isRefreshing)
        assertFalse(finished.isRefreshing)
        assertTrue(finished.inputs.refresh.finished)
        assertEquals(AppError.Network, finished.inputs.refresh.error)
    }

    @Test
    fun `equal content keeps its instance so unchanged rows skip recomposition`() {
        val refreshing = reduce(SpotPickerResult.RefreshStarted, from = loaded)

        assertSame(loaded.content, refreshing.content)
    }
}
