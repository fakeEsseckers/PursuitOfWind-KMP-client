package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerEffect
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerScreen
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel.SpotPickerViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Connects [SpotPickerScreen] to its [SpotPickerViewModel]; [onFinished] runs once the choices are saved. No layout here. */
@Composable
internal fun SpotPickerRoute(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpotPickerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SpotPickerEffect.Finished -> onFinished()
        }
    }
    SpotPickerScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}
