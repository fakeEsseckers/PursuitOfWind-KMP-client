package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

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
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper.RideOfferStateMapper
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferEffect
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferIntent
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import io.github.esseckers.pursuitofwind.feature.spotdetail.BERLIN
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SUNDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The ride offer form's view model on the real data layer. */
@OptIn(ExperimentalCoroutinesApi::class)
class RideOfferViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val koin: Koin = testGraph(clock).koin
    private val viewModels = ViewModelStore()
    private val draft = RideDraft(NORDSTRAND, at(SUNDAY, 13), at(SUNDAY, 17), at(SUNDAY, 10, 30))

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

    private fun viewModel(): RideOfferViewModel =
        ViewModelProvider.create(
            viewModels,
            viewModelFactory {
                initializer {
                    val mapper = RideOfferStateMapper.at(draft, clock, BERLIN)
                    // No ticks: the tests move the clock themselves.
                    RideOfferViewModel(draft, repository = koin.get(), mapper = mapper, clock = clock, tick = Duration.INFINITE)
                }
            },
        )[RideOfferViewModel::class]

    private suspend fun riderInWeekendCrew() {
        koin.get<ProfileRepository>().save(alex()).value()
        koin.get<SpotRepository>().follow(NORDSTRAND, 2.hours, favorite = true).value()
        koin.get<CrewRepository>().join("WIND-4KQ7").value()
    }

    @Test
    fun `an offered ride goes to the crew with its seats and meeting point and the form closes`() =
        runTest(dispatcher) {
            riderInWeekendCrew()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.summary != null && it.crews.isNotEmpty() }
                viewModel.onIntent(RideOfferIntent.MoreSeats)
                viewModel.onIntent(RideOfferIntent.ChangeMeetingPoint("  Old harbour car park "))
                viewModel.onIntent(RideOfferIntent.Submit)
                awaitState { it.sending }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(RideOfferEffect.Offered, awaitItem()) }

            val ride =
                koin
                    .get<RideRepository>()
                    .rides("crw_weekend")
                    .first()
                    .value
                    .single { it.driver.displayName == "Alex" }
            assertEquals(
                listOf(at(SUNDAY, 13), at(SUNDAY, 17), at(SUNDAY, 10, 30)),
                listOf(ride.windowStart, ride.windowEnd, ride.departAt),
            )
            assertEquals(4, ride.seatsTotal)
            assertEquals("Old harbour car park", ride.meetingPoint)
        }

    @Test
    fun `an offer sent after its departure has passed leaves at the next quarter hour`() =
        runTest(dispatcher) {
            riderInWeekendCrew()
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.summary != null && it.crews.isNotEmpty() }
                // The form stayed open past the drafted 10:30.
                clock.set(at(SUNDAY, 10, 30) + 10.minutes)
                viewModel.onIntent(RideOfferIntent.ChangeMeetingPoint("Old harbour"))
                viewModel.onIntent(RideOfferIntent.Submit)
                awaitState { it.sending }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(RideOfferEffect.Offered, awaitItem()) }

            val ride =
                koin
                    .get<RideRepository>()
                    .rides("crw_weekend")
                    .first()
                    .value
                    .single { it.driver.displayName == "Alex" }
            assertEquals(at(SUNDAY, 10, 45), ride.departAt)
        }

    @Test
    fun `an offer that cannot go out stays in the form with the reason`() =
        runTest(dispatcher) {
            riderInWeekendCrew()
            koin.get<FakePursuitOfWindServer>().networkMode = NetworkMode.OFFLINE
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { it.crews.isNotEmpty() }
                viewModel.onIntent(RideOfferIntent.ChangeMeetingPoint("Old harbour"))
                viewModel.onIntent(RideOfferIntent.Submit)

                val failed = awaitState { it.error != null }
                assertEquals(Res.string.error_network, failed.error)
                assertEquals("Old harbour", failed.meetingPoint)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the app graph creates the view model for a draft`() =
        runTest(dispatcher) {
            val fromGraph =
                ViewModelProvider.create(
                    viewModels,
                    viewModelFactory {
                        initializer {
                            koin.get<RideOfferViewModel> {
                                org.koin.core.parameter
                                    .parametersOf(draft)
                            }
                        }
                    },
                )

            assertIs<RideOfferViewModel>(fromGraph[RideOfferViewModel::class])
        }

    private suspend fun ReceiveTurbine<RideOfferState>.awaitState(predicate: (RideOfferState) -> Boolean): RideOfferState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private companion object {
        val TURBINE_TIMEOUT = 10.seconds
    }
}
