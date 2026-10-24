package io.github.esseckers.pursuitofwind.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base of every screen's ViewModel (MVI, without a library).
 *
 * - The view renders [state] and sends [Intent]s to [onIntent]; it never changes the state itself.
 * - The ViewModel turns intents, and the data it observes in [bind], into [Result]s and passes them to [dispatchResult].
 * - [reducer] folds each result into the next [State], one at a time.
 * - One-off [Effect]s (navigate, show a message) go out through [effects], each delivered to one collector once;
 *   effects sent while nobody collects wait for the next collector. A screen without them uses `Nothing`.
 *
 * [bind] is collected while the screen is shown: from the first collector of [state] until [STOP_AFTER] after the last
 * one leaves. A screen in the background stops observing data, and a configuration change does not restart it.
 */
internal abstract class MviViewModel<State, Intent, Result, Effect>(
    initialState: State,
    private val reducer: Reducer<State, Result>,
) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()

    // Unlimited, so an effect sent while nobody collects waits for the view instead of being dropped.
    private val effectChannel = Channel<Effect>(Channel.UNLIMITED)
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    /** The state as it is now, for decisions in the ViewModel. */
    protected val currentState: State
        get() = mutableState.value

    init {
        viewModelScope.launch {
            // Starts only once the view collects the state, so subclasses are fully constructed by then.
            SharingStarted
                .WhileSubscribed(STOP_AFTER.inWholeMilliseconds)
                .command(mutableState.subscriptionCount)
                .collectLatest { command -> if (command == SharingCommand.START) bind().collect(::dispatchResult) }
        }
    }

    abstract fun onIntent(intent: Intent)

    /** Results from the data the screen observes while it is shown. */
    protected open fun bind(): Flow<Result> = emptyFlow()

    protected fun dispatchResult(result: Result) {
        mutableState.update { reducer.reduce(it, result) }
    }

    protected fun postEffect(effect: Effect) {
        effectChannel.trySend(effect)
    }

    companion object {
        /** Long enough to survive a configuration change, short enough not to observe data in the background. */
        val STOP_AFTER: Duration = 5.seconds
    }
}
