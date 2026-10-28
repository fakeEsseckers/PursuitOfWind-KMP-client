package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapEffect
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.view.SpotMapScreen
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel.SpotMapViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Connects [SpotMapScreen] to its [SpotMapViewModel] and to the platform's [map]. Opened on [editing], it changes that
 * spot; [onFinished] runs once a spot is saved, followed or changed. No layout here.
 */
@Composable
internal fun SpotMapRoute(
    editing: SpotId?,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    map: SpotMap = koinInject(),
    viewModel: SpotMapViewModel = koinViewModel { parametersOf(editing) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SpotMapEffect.Finished -> onFinished()
        }
    }
    SpotMapScreen(state = state, map = map, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}
