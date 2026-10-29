package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.platform.TextClipboard
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.spots_coordinates_copied
import io.github.esseckers.pursuitofwind.core.resources.spots_coordinates_label
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerEffect
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerScreen
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel.SpotPickerViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Connects [SpotPickerScreen] to its [SpotPickerViewModel]; [onFinished] runs once the choices are saved, [onOpenMap]
 * when the rider goes to add a spot on the map. Coordinates the rider copies go to the system [clipboard]. No layout
 * here.
 */
@Composable
internal fun SpotPickerRoute(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpotPickerViewModel = koinViewModel(),
    map: SpotMap = koinInject(),
    clipboard: TextClipboard = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val label = stringResource(Res.string.spots_coordinates_label)
    val copied = stringResource(Res.string.spots_coordinates_copied)
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SpotPickerEffect.Finished -> {
                onFinished()
            }

            is SpotPickerEffect.CopyCoordinates -> {
                // Copied at once, even if the screen closes next. Where the system shows what was copied, the app
                // says nothing more; otherwise the newest copy replaces the message of the one before.
                if (!clipboard.copy(label, effect.coordinates)) {
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(copied)
                    }
                }
            }
        }
    }
    SpotPickerScreen(
        state = state,
        map = map,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onOpenMap = onOpenMap,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
