package io.github.esseckers.pursuitofwind.core.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
