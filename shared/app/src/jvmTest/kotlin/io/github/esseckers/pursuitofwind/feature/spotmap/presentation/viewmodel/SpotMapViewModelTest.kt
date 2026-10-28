package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.Event
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapEffect
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapIntent
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapPanel
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import io.github.esseckers.pursuitofwind.testing.HeldRequests
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The map of spots' view model on the real data layer: the in-app fake backend over HTTP and in-memory Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotMapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val follows = HeldRequests { it.method == HttpMethod.Put && it.url.encodedPath.startsWith("/v1/me/spots/") }
    private val adds = HeldRequests { it.method == HttpMethod.Post && it.url.encodedPath == "/v1/spots" }
    private val koin: Koin =
        testGraph(clock, module { single<HttpClientEngine> { adds.engine(follows.engine(get<FakePursuitOfWindServer>())) } }).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()
    private val spots get() = koin.get<SpotRepository>()
    private val viewModels = ViewModelStore()
    private val saltLagoon = SpotId("salt-lagoon")

    /** A beach with no spot within kilometres, and the water about 200 m west of it. */
    private val beach = GeoPoint(54.40, 8.60)
    private val westOfBeach = GeoPoint(54.40, 8.597)

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

    private fun viewModel(editing: SpotId? = null): SpotMapViewModel =
        ViewModelProvider.create(
            viewModels,
            viewModelFactory {
                initializer { SpotMapViewModel(repository = koin.get(), deviceZone = TimeZone.of("Europe/Berlin"), editing = editing) }
            },
        )[SpotMapViewModel::class]

    private suspend fun mySpots(): List<MySpot> = spots.mySpots.first().value

    private suspend fun catalog(): List<Spot> {
        spots.refreshCatalog().value()
        return spots.catalog.first().value
    }

    @Test
    fun `a spot placed on the map is added for everyone and followed as a favorite`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitLoaded()
                viewModel.onIntent(SpotMapIntent.LongPress(beach))
                viewModel.onIntent(SpotMapIntent.Tap(westOfBeach))
                viewModel.onIntent(SpotMapIntent.Rename("  Sand Spit "))
                viewModel.onIntent(SpotMapIntent.ChangeDrive(Step.UP))
                viewModel.onIntent(SpotMapIntent.Save)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotMapEffect.Finished, awaitItem()) }

            val added = catalog().single { it.name == "Sand Spit" }
            assertEquals(beach, added.location)
            assertTrue(abs(added.water.degrees - 270.0) < 1.0, "water at ${added.water.degrees}°")
            assertEquals(koin.get<AccountRepository>().userId.value, added.createdBy)
            assertEquals(TimeZone.of("Europe/Berlin"), added.timeZone)
            assertEquals(listOf(Triple(added.id, 75.minutes, true)), mySpots().map { Triple(it.spot.id, it.drive, it.favorite) })
        }

    @Test
    fun `a tapped spot is followed as a favorite at its drive`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitLoaded()
                viewModel.onIntent(SpotMapIntent.SelectSpot(saltLagoon))
                viewModel.onIntent(SpotMapIntent.ChangeDrive(Step.DOWN))
                viewModel.onIntent(SpotMapIntent.Follow)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotMapEffect.Finished, awaitItem()) }

            assertEquals(listOf(Triple(saltLagoon, 45.minutes, true)), mySpots().map { Triple(it.spot.id, it.drive, it.favorite) })
        }

    @Test
    fun `the rider's own spot opens to be changed and keeps its id`() =
        runTest(dispatcher) {
            val mine = spots.addSpot("Sand Spit", beach, Bearing(270.0), TimeZone.of("Europe/Berlin")).value()
            spots.follow(mine.id, 1.hours, favorite = true).value()
            val moved = GeoPoint(54.41, 8.60)
            val viewModel = viewModel(editing = mine.id)
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                val draft = awaitState { it.panel is SpotMapPanel.Draft }.panel as SpotMapPanel.Draft
                assertEquals("Sand Spit", draft.name)
                viewModel.onIntent(SpotMapIntent.LongPress(moved))
                // The water is to the north now.
                viewModel.onIntent(SpotMapIntent.Tap(GeoPoint(54.412, 8.60)))
                viewModel.onIntent(SpotMapIntent.Rename("North Spit"))
                viewModel.onIntent(SpotMapIntent.Save)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotMapEffect.Finished, awaitItem()) }

            val changed = catalog().single { it.id == mine.id }
            assertEquals("North Spit", changed.name)
            assertEquals(moved, changed.location)
            assertTrue(abs(changed.water.degrees) < 1.0 || abs(changed.water.degrees - 360.0) < 1.0, "water at ${changed.water.degrees}°")
            assertEquals(listOf(Triple(mine.id, 1.hours, true)), mySpots().map { Triple(it.spot.id, it.drive, it.favorite) })
        }

    @Test
    fun `a new spot that could not be followed is followed on the retry, not added twice`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitLoaded()
                viewModel.onIntent(SpotMapIntent.LongPress(beach))
                viewModel.onIntent(SpotMapIntent.Tap(westOfBeach))
                viewModel.onIntent(SpotMapIntent.Rename("Sand Spit"))
                follows.hold()
                viewModel.onIntent(SpotMapIntent.Save)
                follows.awaitHeld()
                server.networkMode = NetworkMode.OFFLINE
                follows.release()

                val failed = awaitState { it.panel.let { panel -> panel is SpotMapPanel.Selected && panel.error != null } }
                val panel = failed.panel as SpotMapPanel.Selected
                assertEquals("Sand Spit", panel.name)
                assertEquals(Res.string.error_network, panel.error)
                assertTrue(panel.mine)

                server.networkMode = NetworkMode.NORMAL
                viewModel.onIntent(SpotMapIntent.Follow)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(SpotMapEffect.Finished, awaitItem())
                expectNoEvents()
            }

            val added = catalog().filter { it.name == "Sand Spit" }
            assertEquals(1, added.size)
            assertEquals(1, adds.sent)
            assertEquals(listOf(added.single().id), mySpots().map { it.spot.id })
        }

    @Test
    fun `a spot without water or a name is not sent`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitLoaded()
                viewModel.onIntent(SpotMapIntent.LongPress(beach))
                viewModel.onIntent(SpotMapIntent.Rename("Sand Spit"))
                viewModel.onIntent(SpotMapIntent.Save)
                viewModel.onIntent(SpotMapIntent.Tap(westOfBeach))
                viewModel.onIntent(SpotMapIntent.Rename(" "))
                viewModel.onIntent(SpotMapIntent.Save)
                advanceUntilIdle()

                // A save starts with a state that says so, before any request.
                assertTrue(cancelAndConsumeRemainingEvents().none { (it as? Event.Item)?.value?.inputs?.saving == true })
            }

            assertEquals(0, adds.sent)
        }

    @Test
    fun `a spot followed but not as a favorite is followed as one again`() =
        runTest(dispatcher) {
            spots.follow(saltLagoon, 90.minutes, favorite = false).value()
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitLoaded()
                viewModel.onIntent(SpotMapIntent.SelectSpot(saltLagoon))
                viewModel.onIntent(SpotMapIntent.Follow)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotMapEffect.Finished, awaitItem()) }

            assertEquals(listOf(Triple(saltLagoon, 90.minutes, true)), mySpots().map { Triple(it.spot.id, it.drive, it.favorite) })
        }

    @Test
    fun `a refresh loads the base map again when it did not load`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitLoaded()
                viewModel.onIntent(SpotMapIntent.BaseMap(loaded = false))
                assertTrue(awaitState { it.baseMapFailed }.baseMapFailed)
                viewModel.onIntent(SpotMapIntent.Refresh)

                assertEquals(1, awaitState { !it.baseMapFailed }.map?.attempt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the app graph creates the view model, with a spot to change or without one`() =
        runTest(dispatcher) {
            val adding =
                ViewModelProvider.create(
                    viewModels,
                    viewModelFactory { initializer { koin.get<SpotMapViewModel> { parametersOf(null) } } },
                )
            val changing = ViewModelStore()
            val editing =
                ViewModelProvider.create(
                    changing,
                    viewModelFactory { initializer { koin.get<SpotMapViewModel> { parametersOf(SpotId("dune-point")) } } },
                )

            assertEquals(
                null,
                adding[SpotMapViewModel::class]
                    .state.value.inputs.editing,
            )
            assertEquals(
                SpotId("dune-point"),
                editing[SpotMapViewModel::class]
                    .state.value.inputs.editing,
            )
            changing.clear()
        }

    /** The spots, after the first refresh. */
    private suspend fun ReceiveTurbine<SpotMapState>.awaitLoaded(): SpotMapState =
        awaitState { it.map?.markers?.size == CATALOG_SIZE && it.inputs.refresh.finished && !it.inputs.refresh.refreshing }

    private suspend fun ReceiveTurbine<SpotMapState>.awaitState(predicate: (SpotMapState) -> Boolean): SpotMapState {
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
