package io.github.esseckers.pursuitofwind.feature.crew.presentation.mapper

import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewChoice
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewInputs
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewRideUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState.Content
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.MemberUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.PersonUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.alex
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.andreisRide
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.details
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.nightOwls
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.now
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.weekendCrew
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.zone
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CrewStateMapperTest {
    private val crews = Cached(listOf(weekendCrew, nightOwls), now)

    private fun inputs(
        rides: List<Ride> = details.rides.value,
        shown: String? = weekendCrew.id,
    ) = CrewInputs(
        crews = crews,
        chosen = shown,
        shown = shown,
        details = details.copy(rides = Cached(rides, now)),
        refresh = RefreshStatus(finished = true),
        now = now,
    )

    private fun map(inputs: CrewInputs): CrewState = CrewStateMapper.map(inputs, zone)

    private fun CrewState.rides(): List<CrewRideUi> = (content as Content.Ready).crew.days.flatMap { it.rides }

    private fun CrewState.ride(id: String): CrewRideUi = rides().single { it.id == id }

    @Test
    fun `the crew loads until the crews are read and then says there is none or why it could not tell`() {
        val noneCached = CrewInputs(crews = Cached(emptyList(), null))

        assertEquals(Content.Loading, map(CrewInputs()).content)
        assertEquals(Content.Loading, map(noneCached).content)
        assertEquals(Content.NoCrew, map(noneCached.copy(refresh = RefreshStatus(finished = true))).content)
        assertEquals(Content.NoCrew, map(CrewInputs(crews = Cached(emptyList(), now))).content)
        assertEquals(
            Content.Error(Res.string.error_network),
            map(noneCached.copy(refresh = RefreshStatus(finished = true, error = AppError.Network))).content,
        )
    }

    @Test
    fun `the crew the ViewModel shows is on screen and the others are there to switch to`() {
        val shown = map(inputs())
        val reading = map(inputs().copy(details = null))
        val notListedYet = map(inputs(shown = "crw_new"))
        val other = map(inputs(shown = nightOwls.id))

        val crew = (shown.content as Content.Ready).crew
        assertEquals("Weekend Crew", crew.name)
        assertEquals("WIND-4KQ7", crew.inviteCode)
        assertEquals(listOf(CrewChoice(weekendCrew.id, "Weekend Crew", true), CrewChoice(nightOwls.id, "Night Owls", false)), shown.crews)
        // Its details are still being read.
        assertEquals(Content.Loading, reading.content)
        // A crew just made, not in the cached list yet: the tab waits for it instead of showing another.
        assertEquals(Content.Loading, notListedYet.content)
        assertEquals(listOf(false, false), notListedYet.crews.map { it.selected })
        // The details at hand belong to Weekend Crew.
        assertEquals(Content.Loading, other.content)
        assertEquals(listOf(false, true), other.crews.map { it.selected })
    }

    @Test
    fun `cached content wins over a failed refresh with a notice above it`() {
        val offline = map(inputs().copy(refresh = RefreshStatus(finished = true, error = AppError.Network)))
        val failed = map(inputs().copy(refresh = RefreshStatus(finished = true, error = AppError.Server(503))))
        val refreshing = map(inputs().copy(refresh = RefreshStatus(refreshing = true, error = AppError.Network)))

        assertEquals(RefreshNotice.OFFLINE, offline.notice)
        assertEquals(RefreshNotice.REFRESH_FAILED, failed.notice)
        assertEquals(null, refreshing.notice)
        assertTrue(refreshing.isRefreshing)
    }

    @Test
    fun `members show the founder first and which one is the rider`() {
        val members = (map(inputs()).content as Content.Ready).crew.members

        assertEquals(
            listOf(
                MemberUi("Andrei", you = false, owner = true),
                MemberUi("Mira", you = false, owner = false),
                MemberUi("Jonas", you = false, owner = false),
                MemberUi("Alex", you = true, owner = false),
            ),
            members,
        )
    }

    @Test
    fun `rides group by the day their window starts and go by departure with local times`() {
        val days = (map(inputs()).content as Content.Ready).crew.days
        val andrei = map(inputs()).ride("rid_andrei")

        assertEquals(listOf(LocalDate(2026, 10, 23), LocalDate(2026, 10, 24)), days.map { it.date })
        assertEquals(listOf(DayLabel.Tomorrow, DayLabel.Weekday(DayOfWeek.SATURDAY)), days.map { it.label })
        assertEquals(listOf("rid_mira", "rid_alex"), days.first().rides.map { it.id })
        assertEquals(TimeRange("13:00", "17:00"), andrei.window)
        assertEquals("10:30", andrei.departAt)
        assertEquals("Nordstrand Bay", andrei.spotName)
        assertEquals(listOf(PersonUi("Mira", you = false)), andrei.passengers)
    }

    @Test
    fun `each ride offers the one thing the rider can do on it`() {
        val state = map(inputs())

        assertEquals(RideActionEntity.TAKE_SEAT, state.ride("rid_andrei").action)
        assertEquals(null, state.ride("rid_jonas").action)
        assertEquals(RideActionEntity.CANCEL, state.ride("rid_alex").action)
        assertEquals(null, state.ride("rid_mira").action)
        assertTrue(state.ride("rid_mira").cancelled)
    }

    @Test
    fun `a passenger can leave and a change waiting for the server shows as pending`() {
        val seated = andreisRide.copy(passengers = andreisRide.passengers + alex, seatsFree = 0)
        val leaving = map(inputs(rides = listOf(seated)))
        val pending = map(inputs(rides = listOf(andreisRide.copy(pending = true))))
        val cancelling = map(inputs().copy(cancelling = setOf("rid_alex")))

        assertEquals(RideActionEntity.LEAVE, leaving.ride("rid_andrei").action)
        assertEquals(listOf(PersonUi("Mira", false), PersonUi("Alex", true)), leaving.ride("rid_andrei").passengers)
        assertTrue(pending.ride("rid_andrei").pending)
        assertTrue(cancelling.ride("rid_alex").pending)
    }

    @Test
    fun `a car that has left keeps no action and a ride that has ended is gone`() {
        val leftAt = map(inputs().copy(now = andreisRide.departAt))
        val ended = map(inputs().copy(now = andreisRide.windowEnd))

        assertTrue(leftAt.ride("rid_andrei").departed)
        assertEquals(null, leftAt.ride("rid_andrei").action)
        assertTrue(ended.rides().none { it.id == "rid_andrei" })
    }

    @Test
    fun `a ride to a spot the cache does not know yet shows the spot id`() {
        val unknown = map(inputs().copy(details = details.copy(spots = emptyMap())))

        assertEquals("nordstrand-bay", unknown.ride("rid_andrei").spotName)
    }

    @Test
    fun `no rides or only rides that have ended leave the days empty`() {
        val yesterday = andreisRide.copy(windowStart = now - 1.days - 4.hours, windowEnd = now - 1.days)

        assertEquals(emptyList(), (map(inputs(rides = emptyList())).content as Content.Ready).crew.days)
        assertEquals(emptyList(), (map(inputs(rides = listOf(yesterday))).content as Content.Ready).crew.days)
    }

    @Test
    fun `the preview data shows Weekend Crew`() {
        assertEquals("Weekend Crew", (CrewPreviewData.ready.content as Content.Ready).crew.name)
    }
}
