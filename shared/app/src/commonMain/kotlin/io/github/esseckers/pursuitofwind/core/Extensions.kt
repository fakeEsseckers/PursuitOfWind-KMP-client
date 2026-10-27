package io.github.esseckers.pursuitofwind.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Handles a screen's one-off [effects] while it is at least started. An effect sent while the screen is stopped waits
 * in the ViewModel until it starts again, so a navigation is never run behind the user's back or lost.
 */
@Composable
internal fun <Effect> CollectEffects(
    effects: Flow<Effect>,
    onEffect: (Effect) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}

/** The clock's time now and after every [period]. Repeated readings are dropped, so a stopped clock costs nothing. */
internal fun Clock.ticks(period: Duration): Flow<Instant> =
    flow {
        while (true) {
            emit(now())
            delay(period)
        }
    }.distinctUntilChanged()

/**
 * Launches [work] and always hands its result to [onFinish], also when the coroutine is cancelled or [work] throws;
 * then [onFinish] gets [unfinished]. A ViewModel ends what it started this way (a refresh, a save), so a cancelled one
 * never leaves the screen refreshing or saving, and never blocks the next.
 *
 * [onFinish] runs once, on the same coroutine, and cannot suspend, because it may run while the coroutine is
 * cancelled. An exception from [work] still reaches the scope after [onFinish].
 */
internal fun <T> CoroutineScope.launchFinishing(
    unfinished: T,
    work: suspend () -> T,
    onFinish: (T) -> Unit,
): Job =
    launch {
        var result = unfinished
        try {
            result = work()
        } finally {
            onFinish(result)
        }
    }
