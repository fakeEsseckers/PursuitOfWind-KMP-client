package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_invalid
import io.github.esseckers.pursuitofwind.core.resources.crew_seat_taken
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.feature.crew.domain.repository.CrewsRepository
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewEffect
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewRideUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState.Content
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState.Notice
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.PersonUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.RideAction
import io.github.esseckers.pursuitofwind.feature.spotdetail.BERLIN
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SUNDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.steadyBreeze
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The Crew tab's view model on the real data layer: the in-app fake backend, whose crew members act on their own. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private var koin: Koin = testGraph(clock).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()
    private val crews get() = koin.get<CrewRepository>()
    private val viewModels = ViewModelStore()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        koin.close()
        releaseMainDispatcher()
    }

    /** Andrei's car one seat from full, and Jonas, who wants that seat, always gets it first. */
    private fun lastSeatRace() {
        koin.close()
        val seed = steadyBreeze(clock)
        val race =
            seed.copy(
                rides = listOf(seed.rides.single().copy(seatsTotal = 2)),
                memberActivity = seed.memberActivity!!.copy(lastSeatRace = 1.0),
            )
        koin = testGraph(clock, module { single { FakePursuitOfWindServer(get(), race) } }).koin
    }

    private fun viewModel(
        poll: Duration = Duration.INFINITE,
        tick: Duration = Duration.INFINITE,
        repository: CrewsRepository = koin.get(),
    ): CrewViewModel =
        ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { CrewViewModel(repository, clock, riderZone = BERLIN, poll = poll, tick = tick) } },
        )[CrewViewModel::class]

    private suspend fun joined() {
        koin.get<ProfileRepository>().save(alex()).value()
        crews.join("WIND-4KQ7").value()
    }

    @Test
    fun `a new rider has no crew and joins the Weekend Crew with its code`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.content == Content.NoCrew }
                viewModel.onIntent(CrewIntent.StartJoin)
                viewModel.onIntent(CrewIntent.JoinCrew(" wind-4kq7 "))
                val ready = awaitState { state -> state.rides().isNotEmpty() && state.dialog == null }

                val crew = (ready.content as Content.Ready).crew
                assertEquals("Weekend Crew", crew.name)
                assertEquals(listOf("Andrei", "Mira", "Jonas"), crew.members.take(3).map { it.name })
                assertEquals(RideAction.TAKE_SEAT, ready.ride(SEEDED_RIDE).action)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(CrewEffect.Joined("Weekend Crew"), awaitItem()) }
        }

    @Test
    fun `a code no crew has keeps the dialog open and says so`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.content == Content.NoCrew }
                viewModel.onIntent(CrewIntent.StartJoin)
                viewModel.onIntent(CrewIntent.JoinCrew("WIND-0000"))

                val failed = awaitState { (it.dialog as? CrewDialog.Join)?.error != null }
                assertEquals(CrewDialog.Join(sending = false, error = Res.string.crew_invite_invalid), failed.dialog)
                assertEquals(Content.NoCrew, failed.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a new crew shows with its invite code which goes to the share sheet`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.content == Content.NoCrew }
                viewModel.onIntent(CrewIntent.StartCreate)
                viewModel.onIntent(CrewIntent.CreateCrew("  Night Owls "))
                // The crew shows at once; its members come with the sync that follows.
                val ready = awaitState { state -> (state.content as? Content.Ready)?.crew?.members?.isNotEmpty() == true }

                val crew = (ready.content as Content.Ready).crew
                assertEquals("Night Owls", crew.name)
                assertTrue(crew.inviteCode.matches(Regex("WIND-[A-Z0-9]{4}")), crew.inviteCode)
                assertTrue(crew.members.single().let { it.owner && it.you })
                viewModel.onIntent(CrewIntent.ShareInvite)
                cancelAndIgnoreRemainingEvents()
                viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                    assertEquals(CrewEffect.Created("Night Owls", crew.inviteCode), awaitItem())
                    assertEquals(CrewEffect.ShareInvite("Night Owls", crew.inviteCode), awaitItem())
                }
            }
        }

    @Test
    fun `taking a seat puts the rider in the car and leaving gives it back`() =
        runTest(dispatcher) {
            joined()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.rides().any { ride -> ride.id == SEEDED_RIDE } }
                // The seat shows as pending while the request runs; Room may fold that into the answer, so it is not awaited.
                viewModel.onIntent(CrewIntent.TakeSeat(SEEDED_RIDE))
                val seated = awaitState { it.ride(SEEDED_RIDE).let { ride -> !ride.pending && ride.action == RideAction.LEAVE } }
                assertEquals(listOf(PersonUi("Mira", false), PersonUi("Alex", true)), seated.ride(SEEDED_RIDE).passengers)
                assertEquals(1, seated.ride(SEEDED_RIDE).seatsFree)

                viewModel.onIntent(CrewIntent.LeaveRide(SEEDED_RIDE))
                val left = awaitState { it.ride(SEEDED_RIDE).let { ride -> !ride.pending && ride.action == RideAction.TAKE_SEAT } }
                assertEquals(2, left.ride(SEEDED_RIDE).seatsFree)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the last seat a member took first goes back to full and says why`() =
        runTest(dispatcher) {
            lastSeatRace()
            joined()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.rides().any { ride -> ride.id == SEEDED_RIDE && ride.seatsFree == 1 } }
                viewModel.onIntent(CrewIntent.TakeSeat(SEEDED_RIDE))
                val full = awaitState { it.ride(SEEDED_RIDE).let { ride -> !ride.pending && ride.seatsFree == 0 } }

                assertEquals(listOf(PersonUi("Mira", false), PersonUi("Jonas", false)), full.ride(SEEDED_RIDE).passengers)
                assertEquals(null, full.ride(SEEDED_RIDE).action)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(CrewEffect.Failed(Res.string.crew_seat_taken), awaitItem()) }
        }

    @Test
    fun `cancelling the rider's own ride asks first and then shows it cancelled`() =
        runTest(dispatcher) {
            joined()
            val plan = RidePlan(NORDSTRAND, at(SUNDAY, 13), at(SUNDAY, 17), at(SUNDAY, 10, 30), "Old harbour", seatsTotal = 3)
            val ride = koin.get<RideRepository>().offer("crw_weekend", plan).value()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                // The rider follows no spot, so the spot's name comes with the catalog the refresh fetches.
                awaitState { it.rides().any { mine -> mine.id == ride.id && mine.spotName == "Nordstrand Bay" } }
                viewModel.onIntent(CrewIntent.CancelRide(ride.id))
                val asking = awaitState { it.dialog is CrewDialog.ConfirmCancel }
                assertEquals(CrewDialog.ConfirmCancel(ride.id, "Nordstrand Bay", passengers = 0), asking.dialog)
                viewModel.onIntent(CrewIntent.ConfirmCancel)

                val cancelled = awaitState { it.ride(ride.id).cancelled }
                assertEquals(null, cancelled.ride(ride.id).action)
                assertEquals(null, cancelled.dialog)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a crew made beside another is on screen at once and the other does not show in between`() =
        runTest(dispatcher) {
            joined()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { (it.content as? Content.Ready)?.crew?.name == "Weekend Crew" }
                viewModel.onIntent(CrewIntent.StartCreate)
                viewModel.onIntent(CrewIntent.CreateCrew("Night Owls"))
                awaitState { it.inputs.dialog?.sending == true }
                val afterwards = mutableListOf<CrewState>()
                while ((afterwards.lastOrNull()?.content as? Content.Ready)?.crew?.name != "Night Owls") afterwards += awaitItem()

                val closed = afterwards.dropWhile { it.inputs.dialog != null }
                assertTrue(closed.none { (it.content as? Content.Ready)?.crew?.name == "Weekend Crew" }, closed.toString())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a crew just made stays on screen while a list read before it was saved comes in`() =
        runTest(dispatcher) {
            joined()
            val real = koin.get<CrewsRepository>()
            val lists = MutableSharedFlow<Cached<List<Crew>>>(replay = 1)
            val weekendOnly = real.crews.first()
            lists.emit(weekendOnly)
            // The new crew's first refresh waits for the test, and with it the end of the guard.
            val refreshed = CompletableDeferred<Unit>()
            val repository =
                object : CrewsRepository by real {
                    override val crews = lists

                    override suspend fun refresh(crewId: String): AppError? {
                        refreshed.await()
                        return real.refresh(crewId)
                    }
                }
            val viewModel = viewModel(repository = repository)

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { (it.content as? Content.Ready)?.crew?.name == "Weekend Crew" && it.inputs.refresh.finished }
                viewModel.onIntent(CrewIntent.StartCreate)
                viewModel.onIntent(CrewIntent.CreateCrew("Night Owls"))
                val both = real.crews.first { it.value.size == 2 }
                val owls = both.value.single { it.name == "Night Owls" }.id
                awaitState { it.inputs.shown == owls && it.inputs.dialog == null }

                // The same crews, read before Night Owls was saved, arrive after it.
                val stale = weekendOnly.copy(updatedAt = (weekendOnly.updatedAt ?: clock.now()) - 1.minutes)
                lists.emit(stale)
                val waiting = awaitState { it.inputs.crews == stale }
                assertEquals(owls, waiting.inputs.shown)
                assertEquals(Content.Loading, waiting.content)

                lists.emit(both)
                awaitState { (it.content as? Content.Ready)?.crew?.name == "Night Owls" }

                // Once its first refresh is done, the list decides again: a crew gone from it no longer shows.
                refreshed.complete(Unit)
                lists.emit(weekendOnly.copy(updatedAt = clock.now() + 1.minutes))
                awaitState { (it.content as? Content.Ready)?.crew?.name == "Weekend Crew" }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the rider switches between crews`() =
        runTest(dispatcher) {
            joined()
            val owls = crews.create("Night Owls").value()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                val first = awaitState { it.content is Content.Ready && it.crews.size == 2 }
                // Oldest first: the Weekend Crew shows until the rider picks another.
                assertEquals(listOf("Weekend Crew", "Night Owls"), first.crews.map { it.name })
                assertEquals("Weekend Crew", (first.content as Content.Ready).crew.name)
                viewModel.onIntent(CrewIntent.ChooseCrew(owls.id))
                val owlsShown = awaitState { (it.content as? Content.Ready)?.crew?.name == "Night Owls" }
                assertEquals(emptyList(), (owlsShown.content as Content.Ready).crew.days)
                viewModel.onIntent(CrewIntent.ChooseCrew("crw_weekend"))
                awaitState { (it.content as? Content.Ready)?.crew?.name == "Weekend Crew" }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `what the members do shows as the tab polls the crew`() =
        runTest(dispatcher) {
            joined()
            val viewModel = viewModel(poll = 30.seconds)

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                val before = awaitState { it.rides().isNotEmpty() && it.inputs.refresh.finished }.rides()
                // The members act every one to three minutes of the server's clock; the tab sees it on its next poll.
                clock.advanceBy(10.minutes)

                // A ride offered, or a seat taken or given up: nothing else changes the rides here.
                awaitState { it.rides() != before }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a ride turns departed as the clock moves on while the feed is not fetched`() =
        runTest(dispatcher) {
            joined()
            val viewModel = viewModel(tick = 1.minutes)

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { state -> state.rides().any { it.id == SEEDED_RIDE && !it.departed } && state.inputs.refresh.finished }
                // Saturday 06:00 UTC to Monday 09:00 UTC: Andrei left at 08:30 for the window from 11:00.
                clock.advanceBy(2.days + 3.hours)

                awaitState { state -> state.rides().any { it.id == SEEDED_RIDE && it.departed } }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `offline the saved crew stays with a notice`() =
        runTest(dispatcher) {
            joined()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.rides().isNotEmpty() && it.inputs.refresh.finished }
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(CrewIntent.Refresh)

                val offline = awaitState { it.notice != null }
                assertEquals(Notice.OFFLINE, offline.notice)
                assertTrue(offline.rides().isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the app graph creates the view model of the Crew tab`() =
        runTest(dispatcher) {
            val fromGraph = ViewModelProvider.create(viewModels, viewModelFactory { initializer { koin.get<CrewViewModel>() } })

            assertIs<CrewViewModel>(fromGraph[CrewViewModel::class])
        }

    private fun CrewState.rides(): List<CrewRideUi> =
        (content as? Content.Ready)
            ?.crew
            ?.days
            ?.flatMap { it.rides }
            .orEmpty()

    private fun CrewState.ride(id: String): CrewRideUi = rides().single { it.id == id }

    private suspend fun ReceiveTurbine<CrewState>.awaitState(predicate: (CrewState) -> Boolean): CrewState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        val TURBINE_TIMEOUT = 10.seconds

        /** Andrei's ride in the seed, leaving the day after tomorrow at 08:30 UTC. */
        const val SEEDED_RIDE = "rid_7f3c"
    }
}
