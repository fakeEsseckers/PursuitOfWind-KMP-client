package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.feature.spotdetail.BERLIN
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SUNDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailIntent
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Missing
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The spot detail view model on the real data layer: the in-app fake backend over HTTP, in-memory Room, DataStore,
 * wired by the app's own Koin modules, with steady 18 kn from 240° at every spot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val koin: Koin = testGraph(clock).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()
    private val viewModels = ViewModelStore()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        koin.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(
        spotId: SpotId = NORDSTRAND,
        date: LocalDate? = null,
    ): SpotDetailViewModel =
        ViewModelProvider.create(
            viewModels,
            viewModelFactory {
                initializer {
                    SpotDetailViewModel(
                        spotId,
                        date,
                        repository = koin.get(),
                        clock = clock,
                        riderZone = BERLIN,
                        tick = Duration.INFINITE,
                    )
                }
            },
        )[SpotDetailViewModel::class]

    private suspend fun riderFollowingNordstrand() {
        koin.get<ProfileRepository>().save(alex()).value()
        koin.get<SpotRepository>().follow(NORDSTRAND, 2.hours, favorite = true).value()
    }

    @Test
    fun `a followed spot shows its forecast at the day it was opened for`() =
        runTest(dispatcher) {
            riderFollowingNordstrand()

            viewModel(date = SUNDAY).state.test(timeout = TURBINE_TIMEOUT) {
                val ready = awaitReady()
                val content = ready.content as Content.Ready

                assertEquals("Nordstrand Bay", ready.header?.name)
                assertEquals(
                    at(SATURDAY, 8),
                    content.chart.hours
                        .first()
                        .start,
                )
                assertEquals(
                    SUNDAY,
                    content.selected.start
                        .toLocalDateTime(BERLIN)
                        .date,
                )
                assertEquals(Verdict.GO, content.selected.verdict)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a tap on an hour shows its details`() =
        runTest(dispatcher) {
            riderFollowingNordstrand()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitReady()
                viewModel.onIntent(SpotDetailIntent.SelectHour(at(SATURDAY, 20)))
                val evening = awaitState { (it.content as? Content.Ready)?.selected?.start == at(SATURDAY, 20) }

                assertEquals(Verdict.NO_GO, (evening.content as Content.Ready).selected.verdict)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a forecast fetched minutes ago is not fetched again`() =
        runTest(dispatcher) {
            riderFollowingNordstrand()
            koin.get<ForecastRepository>().refresh(NORDSTRAND).value()

            viewModel().state.test(timeout = TURBINE_TIMEOUT) {
                val states = mutableListOf<SpotDetailState>()
                do states += awaitItem() while (states.last().content !is Content.Ready)

                assertFalse(states.any { it.isRefreshing })
                assertEquals(
                    null,
                    states
                        .last()
                        .inputs.refresh.startedAt,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `offline with nothing cached shows an error and a retry recovers`() =
        runTest(dispatcher) {
            riderFollowingNordstrand()
            server.networkMode = NetworkMode.OFFLINE
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(Content.Error(Res.string.error_network), awaitState { it.content is Content.Error }.content)
                server.networkMode = NetworkMode.NORMAL
                viewModel.onIntent(SpotDetailIntent.Refresh)
                awaitReady()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a spot the rider does not follow says so`() =
        runTest(dispatcher) {
            riderFollowingNordstrand()

            viewModel(spotId = SpotId("salt-lagoon")).state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(Content.Missing(Missing.SPOT), awaitState { it.content is Content.Missing }.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the app graph creates the view model with its spot and day`() =
        runTest(dispatcher) {
            val fromGraph =
                ViewModelProvider.create(
                    viewModels,
                    viewModelFactory { initializer { koin.get<SpotDetailViewModel> { parametersOf(NORDSTRAND, SATURDAY) } } },
                )

            val viewModel = assertIs<SpotDetailViewModel>(fromGraph[SpotDetailViewModel::class])
            assertEquals(NORDSTRAND, viewModel.state.value.inputs.spotId)
        }

    private suspend fun ReceiveTurbine<SpotDetailState>.awaitReady(): SpotDetailState =
        awaitState {
            it.content is Content.Ready &&
                !it.isRefreshing
        }

    private suspend fun ReceiveTurbine<SpotDetailState>.awaitState(predicate: (SpotDetailState) -> Boolean): SpotDetailState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        val TURBINE_TIMEOUT = 10.seconds
    }
}
