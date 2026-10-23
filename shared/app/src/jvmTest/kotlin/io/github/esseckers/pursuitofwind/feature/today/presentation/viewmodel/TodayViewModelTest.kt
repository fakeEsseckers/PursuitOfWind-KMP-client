package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.BERLIN
import io.github.esseckers.pursuitofwind.feature.today.FRIDAY
import io.github.esseckers.pursuitofwind.feature.today.SATURDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.alex
import io.github.esseckers.pursuitofwind.feature.today.at
import io.github.esseckers.pursuitofwind.feature.today.hour
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayEffect
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Missing
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Notice
import io.github.esseckers.pursuitofwind.feature.today.ride
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The Today view model on the real data layer: the in-app fake backend over HTTP, in-memory Room, DataStore in a
 * temporary directory, wired by the app's own Koin modules. Only the forecast is made steady, so verdicts are known.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(THURSDAY_8AM)
    private val koin: Koin = testGraph(clock).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()

    /** Owns the view models, so each test can clear its own before the database closes. */
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

    private fun viewModel(tick: Duration = Duration.INFINITE): TodayViewModel =
        ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { TodayViewModel(repository = koin.get(), clock = clock, riderZone = BERLIN, tick = tick) } },
        )[
            TodayViewModel::class,
        ]

    private suspend fun riderWithFavorite(favorite: Boolean = true) {
        koin.get<ProfileRepository>().save(alex()).value()
        koin.get<SpotRepository>().follow(SpotId("nordstrand-bay"), 2.hours, favorite).value()
    }

    @Test
    fun `a new rider is asked for a profile`() =
        runTest(dispatcher) {
            viewModel().state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(Content.Empty(Missing.PROFILE), awaitState { it.content !is Content.Loading }.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a rider who has no favorite spot is asked to pick one`() =
        runTest(dispatcher) {
            riderWithFavorite(favorite = false)

            viewModel().state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(Content.Empty(Missing.FAVORITE_SPOTS), awaitState { it.content !is Content.Loading }.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a favorite spot with wind gives three GO days and the ride of the crew on Saturday`() =
        runTest(dispatcher) {
            riderWithFavorite()
            koin.get<CrewRepository>().join("WIND-4KQ7").value()

            viewModel().state.test(timeout = TURBINE_TIMEOUT) {
                // The cache settles after the refresh ends: wait until the synced ride has reached the card.
                val ready = awaitState { state -> state.days().getOrNull(2)?.let { it is DayUi.Go && it.rides.isNotEmpty() } == true }
                val days = ready.days().map { assertIs<DayUi.Go>(it) }

                assertEquals(listOf(THURSDAY, FRIDAY, SATURDAY), days.map { it.date })
                assertEquals(listOf(DayLabel.Today, DayLabel.Tomorrow), days.take(2).map { it.label })
                assertTrue(days.all { it.spotName == "Nordstrand Bay" && it.kiteM2 == 9.0 })
                assertTrue(days[0].leaveNow, "At 08:00 the 05:30 departure for today has passed")
                assertEquals(
                    listOf(
                        RideUi(
                            id = "rid_7f3c",
                            driverName = "Andrei",
                            departAt = "10:30",
                            departed = false,
                            seatsFree = 2,
                            youDrive = false,
                            youRide = false,
                            pending = false,
                        ),
                    ),
                    days[2].rides,
                )
                assertEquals(Duration.ZERO, ready.updatedAgo)
                assertNull(ready.notice)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `offline with nothing cached shows an error and a retry recovers`() =
        runTest(dispatcher) {
            server.networkMode = NetworkMode.OFFLINE
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(Content.Error(Res.string.error_network), awaitState { it.content is Content.Error }.content)

                server.networkMode = NetworkMode.NORMAL
                viewModel.onIntent(TodayIntent.Refresh)

                assertEquals(Content.Empty(Missing.PROFILE), awaitState { it.content is Content.Empty }.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cached answers stay when a refresh fails and the screen says it is offline`() =
        runTest(dispatcher) {
            riderWithFavorite()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.content is Content.Ready && !it.isRefreshing }
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(TodayIntent.Refresh)

                val offline = awaitState { it.notice != null }
                assertEquals(Notice.OFFLINE, offline.notice)
                assertIs<Content.Ready>(offline.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `answers follow the clock while the screen is open`() =
        runTest(dispatcher) {
            riderWithFavorite()

            viewModel(tick = 1.minutes).state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.content is Content.Ready && !it.isRefreshing }
                clock.set(at(THURSDAY, 20))

                val evening = awaitState { (it.content as? Content.Ready)?.days?.first() is DayUi.NoGo }
                val today = assertIs<DayUi.NoGo>(assertIs<Content.Ready>(evening.content).days.first())
                assertEquals(Reason.WINDOW_TOO_SHORT, today.reason)
                assertEquals(Notice.STALE, evening.notice)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `coming back to the screen after half an hour refreshes it`() =
        runTest(dispatcher) {
            riderWithFavorite()
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.content is Content.Ready && !it.isRefreshing }
                cancelAndIgnoreRemainingEvents()
            }

            advanceTimeBy(10.seconds) // away long enough for the state to stop being shared
            clock.advanceBy(1.hours)

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                // An hour later the cached forecast is an hour old; the refresh on return makes it current again.
                awaitState { it.content is Content.Ready && it.updatedAgo == Duration.ZERO && !it.isRefreshing }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `opening a spot asks the app to navigate there once`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onIntent(TodayIntent.OpenSpot(SpotId("nordstrand-bay"), SATURDAY))

            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(TodayEffect.OpenSpot(SpotId("nordstrand-bay"), SATURDAY), awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `the app graph creates the view model of the Today screen`() =
        runTest(dispatcher) {
            val fromGraph = ViewModelProvider.create(viewModels, viewModelFactory { initializer { koin.get<TodayViewModel>() } })

            assertIs<TodayViewModel>(fromGraph[TodayViewModel::class])
        }

    private suspend fun ReceiveTurbine<TodayState>.awaitState(predicate: (TodayState) -> Boolean): TodayState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private fun TodayState.days(): List<DayUi> = (content as? Content.Ready)?.days.orEmpty()

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        val TURBINE_TIMEOUT = 10.seconds
    }
}
