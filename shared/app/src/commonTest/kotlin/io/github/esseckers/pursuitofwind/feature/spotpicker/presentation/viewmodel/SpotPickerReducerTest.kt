package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_server
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotpicker.following
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.DriveLimits
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
    fun `the drive of a followed spot changes from where it was and stays within the API limits`() {
        val longer = reduce(SpotPickerResult.DriveChanged(nordstrand.id, 135), from = loaded)
        val tooLong = reduce(SpotPickerResult.DriveChanged(nordstrand.id, DriveLimits.API_MAX_MINUTES + 15), from = loaded)
        val negative = reduce(SpotPickerResult.DriveChanged(nordstrand.id, -15), from = loaded)

        assertEquals(135.minutes, longer.spot("nordstrand-bay").drive)
        assertTrue(longer.spot("nordstrand-bay").selected)
        assertEquals(DriveLimits.API_MAX_MINUTES.minutes, tooLong.spot("nordstrand-bay").drive)
        assertEquals(0.minutes, negative.spot("nordstrand-bay").drive)
    }

    @Test
    fun `a drive longer than the plus button reaches steps down from where it is`() {
        val far = reduce(SpotPickerResult.DataChanged(snapshot(following(nordstrand, 15.hours))), SpotPickerResult.RefreshFinished(null))
        val shorter = reduce(SpotPickerResult.DriveChanged(nordstrand.id, 900 - DriveLimits.STEP_MINUTES), from = far)

        assertFalse(far.spot("nordstrand-bay").canLengthen)
        assertEquals(885.minutes, shorter.spot("nordstrand-bay").drive)
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
