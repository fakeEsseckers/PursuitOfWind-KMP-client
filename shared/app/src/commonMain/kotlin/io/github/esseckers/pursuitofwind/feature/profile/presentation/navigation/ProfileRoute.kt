package io.github.esseckers.pursuitofwind.feature.profile.presentation.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.NotificationPermission
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.profile_saved
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEffect
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileIntent
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.view.ProfileScreen
import io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Connects [ProfileScreen] to its [ProfileViewModel]. In [ProfileMode.SET_UP], a saved profile moves on with
 * [onContinue] and [onBack] returns to Welcome; in [ProfileMode.TAB], it shows "Profile saved". No layout here.
 *
 * The alerts about new session windows are offered where the device can check for them while the app is closed
 * ([BackgroundRefreshScheduler.available]). The system's say on notifications ([NotificationPermission]) is read each
 * time the screen comes back, so a change made in the system's settings shows at once, and asked for when the rider
 * turns the alerts on.
 */
@Composable
internal fun ProfileRoute(
    mode: ProfileMode,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
    scheduler: BackgroundRefreshScheduler = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(Res.string.profile_saved)
    val notifications: NotificationPermission? = if (scheduler.available) koinInject() else null
    if (notifications != null) {
        LifecycleResumeEffect(notifications) {
            val check = scope.launch { viewModel.onIntent(ProfileIntent.NotificationsChecked(notifications.granted())) }
            onPauseOrDispose { check.cancel() }
        }
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.Saved -> {
                when (mode) {
                    ProfileMode.SET_UP -> onContinue()
                    ProfileMode.TAB -> scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                }
            }

            ProfileEffect.AskForNotifications -> {
                notifications?.let { scope.launch { viewModel.onIntent(ProfileIntent.NotificationsChecked(it.request())) } }
            }

            ProfileEffect.OpenNotificationSettings -> {
                notifications?.openSettings()
            }
        }
    }
    ProfileScreen(
        state = state,
        mode = mode,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        alertsOffered = notifications != null,
        modifier = modifier,
    )
}
