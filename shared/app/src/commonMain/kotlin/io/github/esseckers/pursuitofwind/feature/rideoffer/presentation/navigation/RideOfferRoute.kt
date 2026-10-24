package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.mvi.CollectEffects
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferEffect
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view.RideOfferScreen
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Connects [io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view.RideOfferScreen] for [draft] to its [RideOfferViewModel]. [onDone] closes the form once the ride is offered. */
@Composable
internal fun RideOfferRoute(
    draft: RideDraft,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RideOfferViewModel = koinViewModel { parametersOf(draft) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RideOfferEffect.Offered -> onDone()
        }
    }
    RideOfferScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}
