package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.entity.RideDraftEntity
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferEffect
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view.RideOfferScreen
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Connects [RideOfferScreen] for [draft] to its [RideOfferViewModel]. [onDone] closes the form once the ride is offered. */
@Composable
internal fun RideOfferRoute(
    draft: RideDraftEntity,
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
