package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.BarIconButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.PrimaryButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.ScreenTopBar
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.StatusBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_back
import io.github.esseckers.pursuitofwind.core.resources.action_done
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.action_skip
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_offline
import io.github.esseckers.pursuitofwind.core.resources.spots_empty_body
import io.github.esseckers.pursuitofwind.core.resources.spots_empty_title
import io.github.esseckers.pursuitofwind.core.resources.spots_error_title
import io.github.esseckers.pursuitofwind.core.resources.spots_intro
import io.github.esseckers.pursuitofwind.core.resources.spots_loading
import io.github.esseckers.pursuitofwind.core.resources.spots_title
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickSpotUi
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Content
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState.Notice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The spot catalog to choose favorites from, each with its drive from home. Stateless; `SpotPickerRoute` connects it
 * to its ViewModel. "Done" stays at the bottom, so it is in reach however long the list is.
 */
@Composable
internal fun SpotPickerScreen(
    state: SpotPickerState,
    onIntent: (SpotPickerIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = { onIntent(SpotPickerIntent.Refresh) }
    Scaffold(
        modifier = modifier,
        topBar = {
            ScreenTopBar(
                title = stringResource(Res.string.spots_title),
                backLabel = stringResource(Res.string.action_back),
                onBack = onBack,
                actions = {
                    BarIconButton(
                        icon = AppIcons.Refresh,
                        label = stringResource(Res.string.action_refresh),
                        onClick = refresh,
                        modifier = Modifier.testTag("spots.refresh"),
                    )
                },
            )
        },
        bottomBar = { DoneBar(state = state, onDone = { onIntent(SpotPickerIntent.Done) }) },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val content = state.content) {
                Content.Loading -> LoadingSpots()
                Content.Empty -> NoSpots()
                is Content.Error -> SpotsError(content.message, refresh)
                is Content.Ready -> SpotList(content.spots, state.notice, onIntent)
            }
        }
    }
}

@Composable
private fun SpotList(
    spots: List<PickSpotUi>,
    notice: Notice?,
    onIntent: (SpotPickerIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxSize().testTag("spots.list"),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(Res.string.spots_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
        }
        notice?.let { item(key = "notice") { NoticeBanner(it) { onIntent(SpotPickerIntent.Refresh) } } }
        items(spots, key = { it.id.value }) { spot ->
            SpotRow(
                spot = spot,
                onToggle = { onIntent(SpotPickerIntent.Toggle(spot.id)) },
                onDrive = { minutes -> onIntent(SpotPickerIntent.ChangeDrive(spot.id, minutes)) },
            )
        }
    }
}

@Composable
private fun NoticeBanner(
    notice: Notice,
    onRetry: () -> Unit,
) {
    val text: StringResource =
        when (notice) {
            Notice.OFFLINE -> Res.string.banner_spots_offline
            Notice.REFRESH_FAILED -> Res.string.banner_spots_failed
        }
    StatusBanner(
        icon = AppIcons.CloudOff,
        text = stringResource(text),
        modifier = Modifier.padding(bottom = Spacing.s).testTag("spots.notice"),
        action = {
            ActionButton(
                text = stringResource(Res.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("spots.retry"),
            )
        },
    )
}

@Composable
private fun DoneBar(
    state: SpotPickerState,
    onDone: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.saveError?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("spots.saveError"),
                )
            }
            // With nothing chosen and nothing to save, the button says what it does: skip this step.
            val skip = state.selectedCount == 0 && !state.hasChanges
            PrimaryButton(
                text = stringResource(if (skip) Res.string.action_skip else Res.string.action_done),
                onClick = onDone,
                busy = state.saving,
                modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).testTag("spots.done"),
            )
        }
    }
}

@Composable
private fun LoadingSpots() {
    val description = stringResource(Res.string.spots_loading)
    Column(
        modifier =
            Modifier
                .widthIn(max = Sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(Spacing.m)
                .semantics { contentDescription = description }
                .testTag("spots.loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        repeat(PLACEHOLDER_ROWS) { Skeleton(modifier = Modifier.fillMaxWidth().height(Sizes.rowPlaceholder)) }
    }
}

@Composable
private fun NoSpots() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("spots.empty")) {
        MessageState(
            icon = AppIcons.Pin,
            title = stringResource(Res.string.spots_empty_title),
            body = stringResource(Res.string.spots_empty_body),
        )
    }
}

@Composable
private fun SpotsError(
    message: StringResource,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("spots.error")) {
        MessageState(
            icon = AppIcons.CloudOff,
            title = stringResource(Res.string.spots_error_title),
            body = stringResource(message),
            action = {
                ActionButton(
                    text = stringResource(Res.string.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag("spots.retry"),
                )
            },
        )
    }
}

private const val PLACEHOLDER_ROWS = 4
