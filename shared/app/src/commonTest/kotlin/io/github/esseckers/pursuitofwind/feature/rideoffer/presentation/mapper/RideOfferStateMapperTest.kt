package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.CrewOption
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.OfferSummary
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.preview.RideOfferPreviewData
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.preview.RideOfferPreviewData.nightOwls
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.preview.RideOfferPreviewData.weekendCrew
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferReducer
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferResult
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RideOfferStateMapperTest {
    private val reducer = RideOfferReducer(RideOfferPreviewData.draft, RideOfferPreviewData.zone)

    private fun reduce(
        vararg results: RideOfferResult,
        from: RideOfferState = RideOfferPreviewData.empty,
    ): RideOfferState = results.fold(from, reducer::reduce)

    @Test
    fun `the form names the spot and its window local to the spot and when the car leaves`() {
        assertEquals(
            OfferSummary("Nordstrand Bay", DayLabel.Weekday(DayOfWeek.SATURDAY), TimeRange("13:00", "17:00"), "10:30"),
            RideOfferPreviewData.empty.summary,
        )
        assertNull(reduce(RideOfferResult.SpotChanged(null)).summary)
    }

    @Test
    fun `the form shows the departure it is given in the rider's time`() {
        val later = reduce(RideOfferResult.Ticked(Instant.parse("2026-10-24T08:40:00Z"), Instant.parse("2026-10-24T08:45:00Z")))

        assertEquals("10:45", later.summary?.departAt)
        assertNull(
            RideOfferStateMapper
                .map(
                    RideOfferPreviewData.empty.inputs.copy(departAt = null),
                    RideOfferPreviewData.draft,
                    RideOfferPreviewData.zone,
                ).summary,
        )
    }

    @Test
    fun `the day is named as seen from now where the rider is`() {
        val friday = reduce(RideOfferResult.Ticked(Instant.parse("2026-10-23T10:00:00Z"), RideOfferPreviewData.draft.departAt))

        assertEquals(DayLabel.Tomorrow, friday.summary?.day)
    }

    @Test
    fun `the crew the ride goes to is the selected one`() {
        val picked = reduce(RideOfferResult.Chosen(nightOwls.id))

        assertEquals(
            listOf(CrewOption(weekendCrew.id, "Weekend Crew", true), CrewOption(nightOwls.id, "Night Owls", false)),
            RideOfferPreviewData.empty.crews,
        )
        assertEquals(listOf(false, true), picked.crews.map { it.selected })
    }

    @Test
    fun `it can be offered once there is a meeting point and not while it is sent`() {
        val blank = reduce(RideOfferResult.MeetingPointChanged("   "))

        assertFalse(RideOfferPreviewData.empty.canSubmit)
        assertFalse(blank.canSubmit)
        assertTrue(RideOfferPreviewData.filled.canSubmit)
        assertFalse(RideOfferPreviewData.sending.canSubmit)
        assertTrue(RideOfferPreviewData.sending.sending)
    }

    @Test
    fun `seats stay within what the API takes and so does the meeting point`() {
        val one = reduce(RideOfferResult.SeatsChanged(0))
        val eight = reduce(RideOfferResult.SeatsChanged(9))
        val long = reduce(RideOfferResult.MeetingPointChanged("x".repeat(200)))

        assertEquals(1, one.seats)
        assertFalse(one.canFewer)
        assertEquals(8, eight.seats)
        assertFalse(eight.canMore)
        assertEquals(3, RideOfferPreviewData.empty.seats)
        assertEquals(120, long.meetingPoint.length)
    }

    @Test
    fun `a failure stays in the form with its reason until the next try`() {
        assertEquals(Res.string.error_network, RideOfferPreviewData.failed.error)
        assertTrue(RideOfferPreviewData.failed.canSubmit)
        assertNull(reduce(RideOfferResult.Sending, from = RideOfferPreviewData.failed).error)
    }

    @Test
    fun `without a crew there is nothing to offer`() {
        val noneChosen = RideOfferPreviewData.filled.inputs.copy(chosen = null)

        assertTrue(RideOfferPreviewData.noCrew.noCrew)
        assertFalse(RideOfferPreviewData.noCrew.canSubmit)
        assertFalse(RideOfferStateMapper.map(noneChosen, RideOfferPreviewData.draft, RideOfferPreviewData.zone).canSubmit)
        assertFalse(RideOfferState().noCrew)
    }
}
