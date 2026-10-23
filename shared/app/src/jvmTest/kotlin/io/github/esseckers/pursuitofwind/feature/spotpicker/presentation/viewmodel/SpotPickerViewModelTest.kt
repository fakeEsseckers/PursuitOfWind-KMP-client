package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

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
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickSpotUi
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerEffect
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Content
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The spot picker's view model on the real data layer: the in-app fake backend over HTTP and in-memory Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val koin: Koin = testGraph(clock).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()
    private val spots get() = koin.get<SpotRepository>()
    private val viewModels = ViewModelStore()
    private val saltLagoon = SpotId("salt-lagoon")

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

    private fun viewModel(): SpotPickerViewModel =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { SpotPickerViewModel(repository = koin.get()) } })[
            SpotPickerViewModel::class,
        ]

    private suspend fun mySpots(): List<MySpot> = spots.mySpots.first().value

    @Test
    fun `opening fetches the whole catalog with the followed spots chosen`() =
        runTest(dispatcher) {
            spots.follow(NORDSTRAND, 2.hours, favorite = true).value()

            viewModel().state.test(timeout = TURBINE_TIMEOUT) {
                val ready = awaitReady()

                assertEquals(
                    listOf("Dune Point", "Harbor Flats", "Lake Weiss", "Nordstrand Bay", "Salt Lagoon"),
                    ready.spots().map { it.name },
                )
                assertEquals(listOf("nordstrand-bay"), ready.spots().filter { it.selected }.map { it.id.value })
                assertEquals(listOf("harbor-flats", "lake-weiss"), ready.spots().filter { !it.verified }.map { it.id.value })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `chosen spots are followed as favorites at their drive when done`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitReady()
                viewModel.onIntent(SpotPickerIntent.Toggle(saltLagoon))
                viewModel.onIntent(SpotPickerIntent.ChangeDrive(saltLagoon, 75))
                viewModel.onIntent(SpotPickerIntent.Done)
                awaitState { !it.saving && !it.hasChanges && it.selectedCount == 1 }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotPickerEffect.Finished, awaitItem()) }

            assertEquals(listOf(Triple(saltLagoon, 75.minutes, true)), mySpots().map { Triple(it.spot.id, it.drive, it.favorite) })
        }

    @Test
    fun `a favorite that is unchosen stays followed but not as a favorite`() =
        runTest(dispatcher) {
            spots.follow(NORDSTRAND, 2.hours, favorite = true).value()
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitReady()
                viewModel.onIntent(SpotPickerIntent.Toggle(NORDSTRAND))
                viewModel.onIntent(SpotPickerIntent.Done)
                awaitState { !it.saving && !it.hasChanges && it.selectedCount == 0 }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf(Triple(NORDSTRAND, 2.hours, false)), mySpots().map { Triple(it.spot.id, it.drive, it.favorite) })
        }

    @Test
    fun `offline with nothing cached shows an error and a retry recovers`() =
        runTest(dispatcher) {
            server.networkMode = NetworkMode.OFFLINE
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(Content.Error(Res.string.error_network), awaitState { it.content is Content.Error }.content)
                server.networkMode = NetworkMode.NORMAL
                viewModel.onIntent(SpotPickerIntent.Refresh)
                awaitReady()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed save keeps the choices and done again saves them`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitReady()
                viewModel.onIntent(SpotPickerIntent.Toggle(saltLagoon))
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(SpotPickerIntent.Done)
                val failed = awaitState { it.saveError != null }
                assertEquals(Res.string.error_network, failed.saveError)
                assertEquals(1, failed.selectedCount)

                server.networkMode = NetworkMode.NORMAL
                viewModel.onIntent(SpotPickerIntent.Done)
                awaitState { !it.saving && !it.hasChanges && it.saveError == null }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(SpotPickerEffect.Finished, awaitItem())
                expectNoEvents()
            }
            assertEquals(listOf(saltLagoon), mySpots().map { it.spot.id })
        }

    @Test
    fun `done with nothing chosen finishes without a request`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitReady()
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(SpotPickerIntent.Done)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotPickerEffect.Finished, awaitItem()) }
        }

    @Test
    fun `the app graph creates the view model of the spot picker`() =
        runTest(dispatcher) {
            val fromGraph = ViewModelProvider.create(viewModels, viewModelFactory { initializer { koin.get<SpotPickerViewModel>() } })

            assertIs<SpotPickerViewModel>(fromGraph[SpotPickerViewModel::class])
        }

    private fun SpotPickerState.spots(): List<PickSpotUi> = (content as? Content.Ready)?.spots.orEmpty()

    /** The whole catalog, after the first refresh. */
    private suspend fun ReceiveTurbine<SpotPickerState>.awaitReady(): SpotPickerState =
        awaitState { it.spots().size == CATALOG_SIZE && !it.isRefreshing }

    private suspend fun ReceiveTurbine<SpotPickerState>.awaitState(predicate: (SpotPickerState) -> Boolean): SpotPickerState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        val TURBINE_TIMEOUT = 10.seconds

        /** The sample catalog of the fake backend. */
        const val CATALOG_SIZE = 5
    }
}
