package io.github.esseckers.pursuitofwind.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

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
