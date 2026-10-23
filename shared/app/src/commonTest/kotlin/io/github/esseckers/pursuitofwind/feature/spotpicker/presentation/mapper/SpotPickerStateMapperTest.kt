package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.mapper

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.model.SpotChange
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.model.SpotPickerSnapshot
import io.github.esseckers.pursuitofwind.feature.spotpicker.following
import io.github.esseckers.pursuitofwind.feature.spotpicker.inputs
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.DriveLimits
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotChoice
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerInputs
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Content
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Notice
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerPreviewData.dune
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerPreviewData.lakeWeiss
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerPreviewData.nordstrand
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerPreviewData.saltLagoon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SpotPickerStateMapperTest {
    private val empty = SpotPickerSnapshot(Cached(emptyList(), null), emptyList())

    @Test
    fun `the catalog shows by name with the followed spots chosen at their drive`() {
        val state = SpotPickerStateMapper.map(inputs(following(nordstrand, 2.hours)))
        val spots = (state.content as Content.Ready).spots

        assertEquals(listOf("Dune Point", "Lake Weiss", "Nordstrand Bay", "Salt Lagoon"), spots.map { it.name })
        assertEquals(listOf(false, false, true, false), spots.map { it.selected })
        assertEquals(2.hours, spots.single { it.selected }.drive)
        assertEquals(DriveLimits.DEFAULT_MINUTES.minutes, spots.first().drive)
        assertFalse(spots.single { it.id == lakeWeiss.id }.verified)
        assertEquals(1, state.selectedCount)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `an empty cache loads until the refresh ends and then says why`() {
        val before = SpotPickerStateMapper.map(SpotPickerInputs())
        val waiting = SpotPickerStateMapper.map(SpotPickerInputs(snapshot = empty))
        val refreshing = SpotPickerStateMapper.map(SpotPickerInputs(snapshot = empty, refresh = RefreshStatus(refreshing = true)))
        val failed =
            SpotPickerStateMapper.map(
                SpotPickerInputs(snapshot = empty, refresh = RefreshStatus(finished = true, error = AppError.Network)),
            )
        val none = SpotPickerStateMapper.map(SpotPickerInputs(snapshot = empty, refresh = RefreshStatus(finished = true)))

        assertEquals(Content.Loading, before.content)
        assertEquals(Content.Loading, waiting.content)
        assertEquals(Content.Loading, refreshing.content)
        assertEquals(Content.Error(Res.string.error_network), failed.content)
        assertEquals(Content.Empty, none.content)
    }

    @Test
    fun `cached spots win over a failed refresh with a notice above them`() {
        val offline = SpotPickerStateMapper.map(inputs().copy(refresh = RefreshStatus(finished = true, error = AppError.Network)))
        val failed = SpotPickerStateMapper.map(inputs().copy(refresh = RefreshStatus(finished = true, error = AppError.Server(503))))
        val retrying = SpotPickerStateMapper.map(inputs().copy(refresh = RefreshStatus(refreshing = true, error = AppError.Network)))

        assertIs<Content.Ready>(offline.content)
        assertEquals(Notice.OFFLINE, offline.notice)
        assertEquals(Notice.REFRESH_FAILED, failed.notice)
        assertEquals(null, retrying.notice)
    }

    @Test
    fun `done follows a newly chosen spot as a favorite at its drive`() {
        val chosen = inputs().copy(choices = mapOf(saltLagoon.id to SpotChoice(selected = true, driveMinutes = 90)))

        assertEquals(listOf(SpotChange(saltLagoon.id, 90.minutes, favorite = true)), SpotPickerStateMapper.changes(chosen))
        assertTrue(SpotPickerStateMapper.map(chosen).hasChanges)
    }

    @Test
    fun `a favorite that is no longer chosen stays followed but not as a favorite`() {
        val unchosen = inputs(following(nordstrand, 2.hours)).copy(choices = mapOf(nordstrand.id to SpotChoice(false, 120)))

        assertEquals(listOf(SpotChange(nordstrand.id, 2.hours, favorite = false)), SpotPickerStateMapper.changes(unchosen))
    }

    @Test
    fun `choices that match how a spot is followed and unchosen spots save nothing`() {
        val same = inputs(following(nordstrand, 2.hours)).copy(choices = mapOf(nordstrand.id to SpotChoice(true, 120)))
        val driveOfUnchosen = inputs().copy(choices = mapOf(dune.id to SpotChoice(false, 30)))

        assertEquals(emptyList(), SpotPickerStateMapper.changes(same))
        assertEquals(emptyList(), SpotPickerStateMapper.changes(driveOfUnchosen))
    }

    @Test
    fun `changes come in catalog order whatever order they were made in`() {
        val chosen =
            inputs(following(nordstrand, 2.hours)).copy(
                choices =
                    linkedMapOf(
                        saltLagoon.id to SpotChoice(true, 60),
                        nordstrand.id to SpotChoice(true, 150),
                        dune.id to SpotChoice(true, 45),
                    ),
            )

        assertEquals(listOf(dune.id, nordstrand.id, saltLagoon.id), SpotPickerStateMapper.changes(chosen).map { it.spotId })
    }

    @Test
    fun `the drive steps stop at zero and at the longest drive`() {
        val bounds =
            inputs().copy(
                choices = mapOf(dune.id to SpotChoice(true, 0), saltLagoon.id to SpotChoice(true, DriveLimits.MAX_MINUTES)),
            )
        val spots = (SpotPickerStateMapper.map(bounds).content as Content.Ready).spots

        assertFalse(spots.single { it.id == dune.id }.canShorten)
        assertFalse(spots.single { it.id == saltLagoon.id }.canLengthen)
        assertTrue(spots.single { it.id == dune.id }.canLengthen)
    }
}
