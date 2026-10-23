package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel.SpotDetailViewModel
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Connects [SpotDetailScreen] to its [SpotDetailViewModel] for [spotId], opened at [date] (local to the spot) if given.
 * [onBack] leaves the screen. No layout here.
 */
@Composable
internal fun SpotDetailRoute(
    spotId: SpotId,
    date: LocalDate?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpotDetailViewModel = koinViewModel { parametersOf(spotId, date) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SpotDetailScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}
