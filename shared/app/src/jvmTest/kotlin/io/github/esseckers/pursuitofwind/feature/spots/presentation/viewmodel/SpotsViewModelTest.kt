package io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel

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
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.MySpotUi
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RemovedSpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsEffect
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsIntent
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The Spots tab's view model on the real data layer: the in-app fake backend over HTTP and in-memory Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val spotSaves = HeldRequests { it.method == HttpMethod.Put && it.url.encodedPath.startsWith("/v1/me/spots/") }
    private val koin: Koin = testGraph(clock, module { single<HttpClientEngine> { spotSaves.engine(get()) } }).koin
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
        // A test that failed while a request was held must not leave its save waiting.
        spotSaves.release()
        viewModels.clear()
        koin.close()
        releaseMainDispatcher()
    }

    private fun viewModel(): SpotsViewModel =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { SpotsViewModel(repository = koin.get()) } })[
            SpotsViewModel::class,
        ]

    private suspend fun following() {
        spots.follow(NORDSTRAND, 2.hours, favorite = true).value()
        spots.follow(saltLagoon, 160.minutes, favorite = false).value()
    }

    private suspend fun mySpots(): List<Triple<SpotId, Int, Boolean>> =
        spots.mySpots
            .first()
            .value
            .map { Triple(it.spot.id, it.drive.inWholeMinutes.toInt(), it.favorite) }

    @Test
    fun `the followed spots show by name after the first refresh`() =
        runTest(dispatcher) {
            following()

            viewModel().state.test(timeout = TURBINE_TIMEOUT) {
                val ready = awaitSettled()

                assertEquals(listOf("Nordstrand Bay", "Salt Lagoon"), ready.spots().map { it.name })
                assertEquals(listOf(true, false), ready.spots().map { it.favorite })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a favorite switch and drive steps are saved as they are made and end at the last value`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                viewModel.onIntent(SpotsIntent.ToggleFavorite(saltLagoon))
                viewModel.onIntent(SpotsIntent.ChangeDrive(saltLagoon, 175))
                viewModel.onIntent(SpotsIntent.ChangeDrive(saltLagoon, 190))
                // Shown at once, before anything is saved.
                val shown = awaitState { state -> state.spots().single { it.id == saltLagoon }.drive == 190.minutes }
                assertEquals(true, shown.spots().single { it.id == saltLagoon }.saving)

                val saved =
                    awaitState { state ->
                        state.spots().none { it.saving } &&
                            state.spots().single { it.id == saltLagoon }.drive == 190.minutes
                    }
                assertEquals(true, saved.spots().single { it.id == saltLagoon }.favorite)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(Triple(NORDSTRAND, 120, true), Triple(saltLagoon, 190, true)), mySpots())
        }

    @Test
    fun `taps while a save is in flight go out together in one more request`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                val before = spotSaves.sent
                spotSaves.hold()
                viewModel.onIntent(SpotsIntent.ToggleFavorite(saltLagoon))
                spotSaves.awaitHeld()
                viewModel.onIntent(SpotsIntent.ChangeDrive(saltLagoon, 175))
                viewModel.onIntent(SpotsIntent.ChangeDrive(saltLagoon, 190))
                viewModel.onIntent(SpotsIntent.ToggleFavorite(saltLagoon))
                spotSaves.release()

                val saved = awaitState { state -> !state.salt().saving }
                assertEquals(190.minutes, saved.salt().drive)
                assertEquals(false, saved.salt().favorite)
                assertEquals(2, spotSaves.sent - before)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(Triple(saltLagoon, 190, false), mySpots().last())
        }

    @Test
    fun `changes made just before the tab is left are all still saved`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                spotSaves.hold()
                viewModel.onIntent(SpotsIntent.ToggleFavorite(saltLagoon))
                spotSaves.awaitHeld()
                // Waits for the held request, which is still running when the tab goes away.
                viewModel.onIntent(SpotsIntent.ChangeDrive(saltLagoon, 175))
                cancelAndIgnoreRemainingEvents()
            }
            viewModels.clear()
            spotSaves.release()

            spots.mySpots.first { cached -> cached.value.any { it.spot.id == saltLagoon && it.drive == 175.minutes } }
            assertEquals(Triple(saltLagoon, 175, true), mySpots().last())
        }

    @Test
    fun `a spot removed while its save is in flight stays removed and its unsent change is dropped`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                val before = spotSaves.sent
                spotSaves.hold()
                viewModel.onIntent(SpotsIntent.ToggleFavorite(saltLagoon))
                spotSaves.awaitHeld()
                // Waits behind the held save, and would follow the spot again after the removal if it were sent.
                viewModel.onIntent(SpotsIntent.ChangeDrive(saltLagoon, 175))
                viewModel.onIntent(SpotsIntent.Remove(saltLagoon))
                assertEquals(listOf("Nordstrand Bay"), awaitState { it.spots().size == 1 }.spots().map { it.name })
                spotSaves.release()

                val removed = awaitState { it.inputs.removing.isEmpty() && it.inputs.saving.isEmpty() }
                assertEquals(listOf("Nordstrand Bay"), removed.spots().map { it.name })
                assertEquals(emptyMap(), removed.inputs.edits)
                assertEquals(1, spotSaves.sent - before)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(
                    SpotsEffect.Removed(RemovedSpot(saltLagoon, "Salt Lagoon", verified = true, 175.minutes, favorite = true)),
                    awaitItem(),
                )
            }
            assertEquals(listOf(NORDSTRAND), mySpots().map { it.first })
        }

    @Test
    fun `undo leaves alone a spot that was followed again meanwhile`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                viewModel.onIntent(SpotsIntent.Remove(saltLagoon))
                awaitState { it.inputs.removing.isEmpty() && it.spots().size == 1 }
                cancelAndIgnoreRemainingEvents()
            }
            // Posted together with the end of the removal, which the state above already shows.
            val removed = assertIs<SpotsEffect.Removed>(viewModel.effects.first()).spot

            // The rider picks it again in the spot picker, with a new drive, and only then taps the old "Undo".
            spots.follow(saltLagoon, 45.minutes, favorite = true).value()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { state -> state.spots().any { it.id == saltLagoon } }
                val before = spotSaves.sent
                viewModel.onIntent(SpotsIntent.Undo(removed))
                // Saved after the undo, so once it is saved the undo is done.
                viewModel.onIntent(SpotsIntent.ToggleFavorite(saltLagoon))
                awaitState { state -> state.spots().singleOrNull { it.id == saltLagoon }?.let { !it.saving && !it.favorite } == true }
                assertEquals(1, spotSaves.sent - before)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(Triple(saltLagoon, 45, false), mySpots().last())
        }

    @Test
    fun `a change that cannot be saved goes back to the cached value and says why`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(SpotsIntent.ToggleFavorite(NORDSTRAND))
                awaitState { state -> state.spots().single { it.id == NORDSTRAND }.let { !it.favorite && it.saving } }
                val reverted = awaitState { state -> state.spots().single { it.id == NORDSTRAND }.let { it.favorite && !it.saving } }
                assertEquals(2.hours, reverted.spots().single { it.id == NORDSTRAND }.drive)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(
                timeout = TURBINE_TIMEOUT,
            ) { assertEquals(SpotsEffect.SaveFailed(Res.string.error_network), awaitItem()) }
            assertEquals(Triple(NORDSTRAND, 120, true), mySpots().first())
        }

    @Test
    fun `a removed spot goes at once and undo brings it back as it was`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                viewModel.onIntent(SpotsIntent.Remove(NORDSTRAND))
                assertEquals(listOf("Salt Lagoon"), awaitItem().spots().map { it.name })
                awaitState { it.inputs.removing.isEmpty() && it.spots().size == 1 }
                cancelAndIgnoreRemainingEvents()
            }
            val removed = RemovedSpot(NORDSTRAND, "Nordstrand Bay", verified = true, 2.hours, favorite = true)
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(SpotsEffect.Removed(removed), awaitItem()) }
            assertEquals(listOf(saltLagoon), mySpots().map { it.first })

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                spotSaves.hold()
                viewModel.onIntent(SpotsIntent.Undo(removed))
                // Back in its place at once, before the server has answered.
                val back = awaitState { state -> state.spots().any { it.id == NORDSTRAND && it.restoring } }
                assertEquals(listOf("Nordstrand Bay", "Salt Lagoon"), back.spots().map { it.name })
                spotSaves.awaitHeld()
                spotSaves.release()
                awaitState { state -> state.spots().size == 2 && state.spots().none { it.restoring } }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(Triple(NORDSTRAND, 120, true), Triple(saltLagoon, 160, false)), mySpots())
        }

    @Test
    fun `an undo that cannot be saved takes the spot away again and says why`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                viewModel.onIntent(SpotsIntent.Remove(NORDSTRAND))
                awaitState { it.inputs.removing.isEmpty() && it.spots().size == 1 }
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(SpotsIntent.Undo(RemovedSpot(NORDSTRAND, "Nordstrand Bay", verified = true, 2.hours, favorite = true)))
                awaitState { state -> state.spots().any { it.restoring } }
                awaitState { state -> state.inputs.restoring.isEmpty() && state.spots().size == 1 }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertIs<SpotsEffect.Removed>(awaitItem())
                assertEquals(SpotsEffect.SaveFailed(Res.string.error_network), awaitItem())
            }
        }

    @Test
    fun `a spot that cannot be removed comes back and the reason is said`() =
        runTest(dispatcher) {
            following()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitSettled()
                server.networkMode = NetworkMode.OFFLINE
                viewModel.onIntent(SpotsIntent.Remove(NORDSTRAND))
                awaitState { it.spots().size == 1 }
                awaitState { it.spots().size == 2 }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(
                timeout = TURBINE_TIMEOUT,
            ) { assertEquals(SpotsEffect.SaveFailed(Res.string.error_network), awaitItem()) }
        }

    @Test
    fun `opening a spot and adding spots ask the app to navigate`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onIntent(SpotsIntent.OpenSpot(NORDSTRAND))
            viewModel.onIntent(SpotsIntent.AddSpots)

            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(SpotsEffect.OpenSpot(NORDSTRAND), awaitItem())
                assertEquals(SpotsEffect.AddSpots, awaitItem())
                expectNoEvents()
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
                viewModel.onIntent(SpotsIntent.Refresh)
                assertEquals(Content.Empty, awaitState { it.content == Content.Empty }.content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the app graph creates the view model of the Spots tab`() =
        runTest(dispatcher) {
            val fromGraph = ViewModelProvider.create(viewModels, viewModelFactory { initializer { koin.get<SpotsViewModel>() } })

            assertIs<SpotsViewModel>(fromGraph[SpotsViewModel::class])
        }

    private fun SpotsState.spots(): List<MySpotUi> = (content as? Content.Ready)?.spots.orEmpty()

    private fun SpotsState.salt(): MySpotUi = spots().single { it.id == saltLagoon }

    /** The list after the first refresh, with nothing waiting to be saved. */
    private suspend fun ReceiveTurbine<SpotsState>.awaitSettled(): SpotsState =
        awaitState { it.inputs.refresh.finished && !it.isRefreshing && it.content is Content.Ready }

    private suspend fun ReceiveTurbine<SpotsState>.awaitState(predicate: (SpotsState) -> Boolean): SpotsState {
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
