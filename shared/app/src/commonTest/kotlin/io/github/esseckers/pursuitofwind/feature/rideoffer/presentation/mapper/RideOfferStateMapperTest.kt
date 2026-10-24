package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.CrewOption
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.OfferSummary
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view.RideOfferPreviewData
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view.RideOfferPreviewData.nightOwls
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view.RideOfferPreviewData.weekendCrew
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferReducer
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferResult
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RideOfferStateMapperTest {
    private val reducer = RideOfferReducer(RideOfferPreviewData.mapper)

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
    fun `the car leaves as drafted while that is ahead and then at the next quarter hour but never after the window starts`() {
        val mapper = RideOfferPreviewData.mapper

        fun leaves(now: String): Instant = mapper.departure(RideOfferPreviewData.empty.inputs.copy(now = Instant.parse(now)))

        assertEquals(RideOfferPreviewData.draft.departAt, mapper.departure(RideOfferPreviewData.empty.inputs))
        assertEquals(Instant.parse("2026-10-24T08:30:00Z"), leaves("2026-10-24T08:29:00Z"))
        assertEquals(Instant.parse("2026-10-24T08:45:00Z"), leaves("2026-10-24T08:31:00Z"))
        assertEquals(Instant.parse("2026-10-24T11:00:00Z"), leaves("2026-10-24T11:05:00Z"))
        assertEquals(
            "10:45",
            reduce(RideOfferResult.Ticked(Instant.parse("2026-10-24T08:40:00Z"))).summary?.departAt,
        )
    }

    @Test
    fun `the day is named as seen from today where the rider is`() {
        val friday =
            RideOfferStateMapper(RideOfferPreviewData.draft, today = LocalDate(2026, 10, 23), riderZone = RideOfferPreviewData.zone)

        assertEquals(DayLabel.Tomorrow, friday.map(RideOfferPreviewData.empty.inputs).summary?.day)
    }

    @Test
    fun `the ride goes to the first crew unless the rider picks another`() {
        val picked = reduce(RideOfferResult.Chosen(nightOwls.id))

        assertEquals(
            listOf(CrewOption(weekendCrew.id, "Weekend Crew", true), CrewOption(nightOwls.id, "Night Owls", false)),
            RideOfferPreviewData.empty.crews,
        )
        assertEquals(listOf(false, true), picked.crews.map { it.selected })
        assertEquals(nightOwls, RideOfferPreviewData.mapper.crewFor(picked.inputs))
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
        assertTrue(RideOfferPreviewData.noCrew.noCrew)
        assertFalse(RideOfferPreviewData.noCrew.canSubmit)
        assertFalse(RideOfferState().noCrew)
    }
}
