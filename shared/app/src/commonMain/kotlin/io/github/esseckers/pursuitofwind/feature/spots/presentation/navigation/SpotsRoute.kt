package io.github.esseckers.pursuitofwind.feature.spots.presentation.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_undo
import io.github.esseckers.pursuitofwind.core.resources.my_spots_removed
import io.github.esseckers.pursuitofwind.core.resources.my_spots_save_failed
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsEffect
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsIntent
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsScreen
import io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel.SpotsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * Connects [SpotsScreen] to its [SpotsViewModel]. [onOpenSpot] navigates to a spot's forecast, [onAddSpots] to the
 * spot picker and [onChangeOnMap] to the map to change a spot the rider added; removals and failed saves become
 * snackbars, a removal with "Undo". No layout here.
 */
@Composable
internal fun SpotsRoute(
    onOpenSpot: (SpotId) -> Unit,
    onAddSpots: () -> Unit,
    onChangeOnMap: (SpotId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpotsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SpotsEffect.OpenSpot -> {
                onOpenSpot(effect.spotId)
            }

            SpotsEffect.AddSpots -> {
                onAddSpots()
            }

            is SpotsEffect.ChangeOnMap -> {
                onChangeOnMap(effect.spotId)
            }

            is SpotsEffect.Removed -> {
                scope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.my_spots_removed, effect.spot.name),
                            actionLabel = getString(Res.string.action_undo),
                        )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onIntent(SpotsIntent.Undo(effect.spot))
                }
            }

            is SpotsEffect.SaveFailed -> {
                scope.launch { snackbarHostState.showSnackbar("${getString(Res.string.my_spots_save_failed)} ${getString(effect.reason)}") }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SpotsScreen(
        state = state,
        onIntent = { intent ->
            // A second tap while the screen is already leaving would wait in the ViewModel and navigate again after back.
            val navigates = intent is SpotsIntent.OpenSpot || intent is SpotsIntent.ChangeOnMap || intent == SpotsIntent.AddSpots
            if (!navigates || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
