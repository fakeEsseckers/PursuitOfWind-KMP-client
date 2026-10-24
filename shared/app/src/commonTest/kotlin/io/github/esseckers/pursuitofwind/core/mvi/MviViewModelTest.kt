package io.github.esseckers.pursuitofwind.core.mvi

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    /** A counter: [Intent.Add] and data from [source] both add to the state; [Intent.Ping] sends an effect. */
    private class CounterViewModel(
        private val source: Flow<Int>,
        private val events: MutableList<String>,
    ) : MviViewModel<Int, CounterViewModel.Intent, Int, String>(0, Reducer { state, result -> state + result }) {
        sealed interface Intent {
            data class Add(
                val amount: Int,
            ) : Intent

            data object Ping : Intent
        }

        override fun onIntent(intent: Intent) {
            when (intent) {
                is Intent.Add -> dispatchResult(intent.amount)
                Intent.Ping -> postEffect("pong at $currentState")
            }
        }

        override fun bind(): Flow<Int> =
            source
                .onStart { events += "bound" }
                .onCompletion { events += "unbound" }
    }

    private val source = MutableSharedFlow<Int>()
    private val events = mutableListOf<String>()

    private fun counter(): CounterViewModel =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { CounterViewModel(source, events) } })[
            CounterViewModel::class,
        ]

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `intents become results that the reducer folds into the state`() =
        runTest(dispatcher) {
            val counter = counter()
            counter.onIntent(CounterViewModel.Intent.Add(2))
            counter.onIntent(CounterViewModel.Intent.Add(3))

            assertEquals(5, counter.state.value)
        }

    @Test
    fun `data is observed only while the state is collected and a little after`() =
        runTest(dispatcher) {
            val counter = counter()
            runCurrent()
            assertEquals(emptyList(), events)

            counter.state.test {
                assertEquals(0, awaitItem())
                runCurrent()
                source.emit(4)
                assertEquals(4, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(MviViewModel.STOP_AFTER - 1.seconds)
            assertEquals(listOf("bound"), events)

            advanceTimeBy(2.seconds)
            assertEquals(listOf("bound", "unbound"), events)
        }

    @Test
    fun `each effect is delivered once`() =
        runTest(dispatcher) {
            val counter = counter()
            counter.onIntent(CounterViewModel.Intent.Add(1))
            counter.onIntent(CounterViewModel.Intent.Ping)

            counter.effects.test {
                assertEquals("pong at 1", awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `collecting again before the stop timeout keeps observing`() =
        runTest(dispatcher) {
            val counter = counter()
            counter.state.test { showFor(1.seconds) }
            advanceTimeBy(MviViewModel.STOP_AFTER - 1.seconds)

            counter.state.test { showFor(MviViewModel.STOP_AFTER * 2) }
            assertEquals(listOf("bound"), events)
        }

    @Test
    fun `coming back after the stop observes the data again`() =
        runTest(dispatcher) {
            val counter = counter()
            counter.state.test { showFor(1.seconds) }
            advanceTimeBy(MviViewModel.STOP_AFTER + 1.seconds)

            counter.state.test {
                assertEquals(0, awaitItem())
                runCurrent()
                source.emit(3)
                assertEquals(3, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("bound", "unbound", "bound"), events)
        }

    @Test
    fun `effects sent while nobody collects all wait for the view`() =
        runTest(dispatcher) {
            val counter = counter()
            repeat(EFFECTS_WHILE_AWAY) { counter.onIntent(CounterViewModel.Intent.Ping) }

            counter.effects.test {
                repeat(EFFECTS_WHILE_AWAY) { assertEquals("pong at 0", awaitItem()) }
                expectNoEvents()
            }
        }

    /** The view collects the state for [duration] of virtual time, then leaves. */
    private suspend fun ReceiveTurbine<Int>.showFor(duration: Duration) {
        awaitItem()
        dispatcher.scheduler.advanceTimeBy(duration)
        dispatcher.scheduler.runCurrent()
        cancelAndIgnoreRemainingEvents()
    }

    private companion object {
        /** More than a buffered channel holds. */
        const val EFFECTS_WHILE_AWAY = 100
    }
}
