package io.github.esseckers.pursuitofwind.feature.profile.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.AppSnackbarHost
import io.github.esseckers.pursuitofwind.core.designsystem.component.NavigationBarScrim
import io.github.esseckers.pursuitofwind.core.designsystem.component.PrimaryButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.ScreenTopBar
import io.github.esseckers.pursuitofwind.core.designsystem.component.TabTitle
import io.github.esseckers.pursuitofwind.core.designsystem.component.contentEndInsets
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_back
import io.github.esseckers.pursuitofwind.core.resources.action_continue
import io.github.esseckers.pursuitofwind.core.resources.action_save
import io.github.esseckers.pursuitofwind.core.resources.profile_fix_fields
import io.github.esseckers.pursuitofwind.core.resources.profile_setup_intro
import io.github.esseckers.pursuitofwind.core.resources.profile_setup_title
import io.github.esseckers.pursuitofwind.core.resources.profile_title
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileIntent
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState
import org.jetbrains.compose.resources.stringResource

/**
 * The rider's profile as a form: onboarding's "About you" ([ProfileMode.SET_UP], with back and "Continue") or the
 * Profile tab ([ProfileMode.TAB], saved in place). Stateless; `ProfileRoute` connects it to its ViewModel.
 * [snackbarHostState] shows the tab's "Profile saved".
 */
@Composable
internal fun ProfileScreen(
    state: ProfileState,
    mode: ProfileMode,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            when (mode) {
                ProfileMode.SET_UP -> {
                    ScreenTopBar(
                        title = stringResource(Res.string.profile_setup_title),
                        backLabel = stringResource(Res.string.action_back),
                        onBack = onBack,
                    )
                }

                ProfileMode.TAB -> {
                    TabTitle(title = stringResource(Res.string.profile_title))
                }
            }
        },
        // Above the keyboard, where "Profile saved" would otherwise land while a field still has focus.
        snackbarHost = { AppSnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        // Reading the saved profile takes a moment; an empty screen is calmer than a spinner that flashes.
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding).testTag("profile.loading"))
            } else {
                ProfileForm(state = state, mode = mode, onIntent = onIntent, modifier = Modifier.padding(padding))
            }
            NavigationBarScrim(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ProfileForm(
    state: ProfileState,
    mode: ProfileMode,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onEdit: (ProfileEdit) -> Unit = { onIntent(ProfileIntent.Edit(it)) }
    Box(modifier = modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = Sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(contentEndInsets())
                    .padding(Spacing.m)
                    .testTag("profile.form"),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (mode == ProfileMode.SET_UP) {
                Text(
                    text = stringResource(Res.string.profile_setup_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AboutYouFields(form = state.form, version = state.formVersion, errors = state.errors, onEdit = onEdit)
            LevelChoice(level = state.form.level, onEdit = onEdit)
            KiteChoices(kites = state.kites, onEdit = onEdit)
            UnitChoice(unit = state.form.unit, onEdit = onEdit)
            LanguageChoice(language = state.language, onChoose = { onIntent(ProfileIntent.ChooseLanguage(it)) })
            MoreSettings(form = state.form, version = state.formVersion, errors = state.errors, onEdit = onEdit)
            SaveArea(state = state, mode = mode, onSave = { onIntent(ProfileIntent.Save) })
        }
    }
}

@Composable
private fun SaveArea(
    state: ProfileState,
    mode: ProfileMode,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // The errors sit under their fields, which may be scrolled away from this button.
        if (state.errors.isNotEmpty()) ErrorLine(text = stringResource(Res.string.profile_fix_fields), tag = "profile.fixFields")
        state.saveError?.let {
            ErrorLine(text = stringResource(it), tag = "profile.saveError")
        }
        PrimaryButton(
            text = stringResource(if (mode == ProfileMode.SET_UP) Res.string.action_continue else Res.string.action_save),
            onClick = onSave,
            // In the tab, there is nothing to save until something changed; onboarding always moves on.
            enabled = mode == ProfileMode.SET_UP || state.hasChanges,
            busy = state.saving,
            modifier = Modifier.testTag("profile.save"),
        )
    }
}

/** Why the button did not go through, announced when it appears. */
@Composable
private fun ErrorLine(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
    )
}
