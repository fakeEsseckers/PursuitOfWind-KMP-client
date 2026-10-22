package io.github.esseckers.pursuitofwind.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Connects [TodayScreen] to its [TodayViewModel]. No layout here. */
@Composable
internal fun TodayRoute(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
