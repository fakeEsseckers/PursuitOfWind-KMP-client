package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerEffect
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerScreen
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel.SpotPickerViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Connects [SpotPickerScreen] to its [SpotPickerViewModel]; [onFinished] runs once the choices are saved, [onOpenMap]
 * when the rider goes to add a spot on the map. No layout here.
 */
@Composable
internal fun SpotPickerRoute(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpotPickerViewModel = koinViewModel(),
    map: SpotMap = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SpotPickerEffect.Finished -> onFinished()
        }
    }
    SpotPickerScreen(state = state, map = map, onIntent = viewModel::onIntent, onBack = onBack, onOpenMap = onOpenMap, modifier = modifier)
}
