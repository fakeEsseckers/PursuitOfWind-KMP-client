package io.github.esseckers.pursuitofwind.feature.crew.presentation.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.esseckers.pursuitofwind.core.CollectEffects
import io.github.esseckers.pursuitofwind.core.link.AppLink
import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_share_invite
import io.github.esseckers.pursuitofwind.core.resources.crew_action_failed
import io.github.esseckers.pursuitofwind.core.resources.crew_created
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_text
import io.github.esseckers.pursuitofwind.core.resources.crew_joined
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewEffect
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import io.github.esseckers.pursuitofwind.feature.crew.presentation.view.CrewScreen
import io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel.CrewViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Connects [CrewScreen] to its [CrewViewModel]. [onOpenSpot] navigates to a spot's forecast; invitations go to the
 * system share sheet ([ShareLauncher]); failures, a new crew (with "Invite friends") and joining become snackbars.
 * An invitation link the app was opened with arrives as [inviteCode]: the join dialog opens with it, and
 * [onInviteTaken] says the link is dealt with. No layout here.
 */
@Composable
internal fun CrewRoute(
    onOpenSpot: (SpotId, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    inviteCode: String? = null,
    onInviteTaken: () -> Unit = {},
    viewModel: CrewViewModel = koinViewModel(),
    shareLauncher: ShareLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // A dialog that is sending its request stays, so the invitation waits for it to end rather than being dropped.
    val sending = state.dialog?.sending == true
    LaunchedEffect(inviteCode, sending) {
        if (inviteCode != null && !sending) {
            viewModel.onIntent(CrewIntent.OpenInvite(inviteCode))
            onInviteTaken()
        }
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CrewEffect.ShareInvite -> {
                scope.launch { shareLauncher.share(inviteText(effect.crewName, effect.inviteCode)) }
            }

            is CrewEffect.Created -> {
                scope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.crew_created, effect.crewName),
                            actionLabel = getString(Res.string.action_share_invite),
                        )
                    // The new crew's code, whichever crew is on screen by the time the rider taps.
                    if (result == SnackbarResult.ActionPerformed) {
                        shareLauncher.share(inviteText(effect.crewName, effect.inviteCode))
                    }
                }
            }

            is CrewEffect.Joined -> {
                scope.launch { snackbarHostState.showSnackbar(getString(Res.string.crew_joined, effect.crewName)) }
            }

            is CrewEffect.Failed -> {
                scope.launch { snackbarHostState.showSnackbar("${getString(Res.string.crew_action_failed)} ${getString(effect.reason)}") }
            }

            is CrewEffect.OpenSpot -> {
                onOpenSpot(effect.spotId, effect.date)
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    CrewScreen(
        state = state,
        onIntent = { intent ->
            // A second tap while the screen is leaving, or while a share sheet covers it, would act again afterwards.
            val leaves = intent is CrewIntent.OpenSpot || intent == CrewIntent.ShareInvite
            if (!leaves || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** The invitation as shared: the crew, its code to type in, and a link that opens the app at the join dialog. */
private suspend fun inviteText(
    crewName: String,
    inviteCode: String,
): String = getString(Res.string.crew_invite_text, crewName, inviteCode, AppLink.Invite(inviteCode).uri)
