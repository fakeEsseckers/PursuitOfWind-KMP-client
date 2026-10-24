package io.github.esseckers.pursuitofwind.feature.spots.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.StatusBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.TabTitle
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_add_spots
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_offline
import io.github.esseckers.pursuitofwind.core.resources.my_spots_empty_body
import io.github.esseckers.pursuitofwind.core.resources.my_spots_empty_title
import io.github.esseckers.pursuitofwind.core.resources.my_spots_error_title
import io.github.esseckers.pursuitofwind.core.resources.my_spots_intro
import io.github.esseckers.pursuitofwind.core.resources.my_spots_loading
import io.github.esseckers.pursuitofwind.core.resources.tab_spots
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.MySpotUi
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsIntent
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Notice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Spots tab: the followed spots, each changed in place and saved at once, and "Add spots" to the catalog.
 * Stateless; `SpotsRoute` connects it to its ViewModel and shows [snackbarHostState]'s messages (removed, with undo;
 * a change that could not be saved). Pull to refresh; screen readers get a "Refresh" action on the title instead.
 */
@Composable
internal fun SpotsScreen(
    state: SpotsState,
    onIntent: (SpotsIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val refresh = { onIntent(SpotsIntent.Refresh) }
    val refreshLabel = stringResource(Res.string.action_refresh)
    val refreshAction =
        remember(refreshLabel, refresh) {
            listOf(
                CustomAccessibilityAction(refreshLabel) {
                    refresh()
                    true
                },
            )
        }
    Scaffold(
        modifier = modifier,
        topBar = {
            TabTitle(
                title = stringResource(Res.string.tab_spots),
                modifier = Modifier.semantics { customActions = refreshAction },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // With no spots, the message offers the same action; one button is enough.
            if (state.content !is Content.Empty) AddSpotsButton(onClick = { onIntent(SpotsIntent.AddSpots) })
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.content != Content.Loading,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val content = state.content) {
                Content.Loading -> LoadingSpots()
                Content.Empty -> NoSpots(onAdd = { onIntent(SpotsIntent.AddSpots) })
                is Content.Error -> SpotsError(content.message, refresh)
                is Content.Ready -> SpotList(content.spots, state.notice, onIntent)
            }
        }
    }
}

@Composable
private fun AddSpotsButton(onClick: () -> Unit) {
    // The overload with a content slot: its text is always shown and read, unlike the one that can fold it away.
    ExtendedFloatingActionButton(onClick = onClick, modifier = Modifier.testTag("myspots.add")) {
        Icon(imageVector = AppIcons.Plus, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(text = stringResource(Res.string.action_add_spots))
    }
}

@Composable
private fun SpotList(
    spots: List<MySpotUi>,
    notice: Notice?,
    onIntent: (SpotsIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxSize().testTag("myspots.list"),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Sizes.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(Res.string.my_spots_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        notice?.let { item(key = "notice") { NoticeBanner(it) { onIntent(SpotsIntent.Refresh) } } }
        items(spots, key = { it.id.value }) { spot ->
            SpotCard(
                spot = spot,
                onToggleFavorite = { onIntent(SpotsIntent.ToggleFavorite(spot.id)) },
                onDrive = { minutes -> onIntent(SpotsIntent.ChangeDrive(spot.id, minutes)) },
                onOpen = { onIntent(SpotsIntent.OpenSpot(spot.id)) },
                onRemove = { onIntent(SpotsIntent.Remove(spot.id)) },
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
        modifier = Modifier.testTag("myspots.notice"),
        action = {
            ActionButton(
                text = stringResource(Res.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("myspots.retry"),
            )
        },
    )
}

@Composable
private fun LoadingSpots() {
    val description = stringResource(Res.string.my_spots_loading)
    Column(
        modifier =
            Modifier
                .widthIn(max = Sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(Spacing.m)
                .semantics { contentDescription = description }
                .testTag("myspots.loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        repeat(PLACEHOLDER_CARDS) { Skeleton(modifier = Modifier.fillMaxWidth().height(Sizes.cardPlaceholder)) }
    }
}

@Composable
private fun NoSpots(onAdd: () -> Unit) {
    // Scrollable, so the pull-to-refresh gesture works on this state too.
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("myspots.empty")) {
        MessageState(
            icon = AppIcons.Pin,
            title = stringResource(Res.string.my_spots_empty_title),
            body = stringResource(Res.string.my_spots_empty_body),
            action = {
                ActionButton(
                    text = stringResource(Res.string.action_add_spots),
                    onClick = onAdd,
                    modifier = Modifier.testTag("myspots.addFirst"),
                )
            },
        )
    }
}

@Composable
private fun SpotsError(
    message: StringResource,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("myspots.error")) {
        MessageState(
            icon = AppIcons.CloudOff,
            title = stringResource(Res.string.my_spots_error_title),
            body = stringResource(message),
            action = {
                ActionButton(
                    text = stringResource(Res.string.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag("myspots.retry"),
                )
            },
        )
    }
}

private const val PLACEHOLDER_CARDS = 2
