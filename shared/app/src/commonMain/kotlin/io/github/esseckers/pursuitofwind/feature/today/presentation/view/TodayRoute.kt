package io.github.esseckers.pursuitofwind.feature.today.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.mvi.CollectEffects
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayEffect
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel.TodayViewModel
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/**
 * Connects [TodayScreen] to its [TodayViewModel]. [onOpenSpot] navigates to a spot's forecast, [onOpenProfile] to the
 * profile and [onPickSpots] to the spot picker. No layout here.
 */
@Composable
internal fun TodayRoute(
    onOpenSpot: (SpotId, LocalDate) -> Unit,
    onOpenProfile: () -> Unit,
    onPickSpots: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is TodayEffect.OpenSpot -> onOpenSpot(effect.spotId, effect.date)
            TodayEffect.OpenProfile -> onOpenProfile()
            TodayEffect.PickSpots -> onPickSpots()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    TodayScreen(
        state = state,
        onIntent = { intent ->
            // A second tap while the screen is already leaving would wait in the ViewModel and navigate again after back.
            if (intent == TodayIntent.Refresh || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onIntent(intent)
        },
        modifier = modifier,
    )
}
