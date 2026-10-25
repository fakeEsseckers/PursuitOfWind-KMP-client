package io.github.esseckers.pursuitofwind.feature.today.presentation.view

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.Disclaimer
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.StatusBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_pick_spots
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.action_set_up_profile
import io.github.esseckers.pursuitofwind.core.resources.banner_crew_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_offline
import io.github.esseckers.pursuitofwind.core.resources.banner_refresh_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_stale
import io.github.esseckers.pursuitofwind.core.resources.empty_profile_body
import io.github.esseckers.pursuitofwind.core.resources.empty_profile_title
import io.github.esseckers.pursuitofwind.core.resources.empty_spots_body
import io.github.esseckers.pursuitofwind.core.resources.empty_spots_title
import io.github.esseckers.pursuitofwind.core.resources.error_title
import io.github.esseckers.pursuitofwind.core.resources.today_loading
import io.github.esseckers.pursuitofwind.core.resources.today_title
import io.github.esseckers.pursuitofwind.core.ui.updatedText
import io.github.esseckers.pursuitofwind.feature.today.presentation.mapper.DAYS_SHOWN
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Missing
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Notice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * "Go?": the answer for today and the next two days. Stateless; `TodayRoute` connects it to [TodayViewModel].
 * Pull to refresh; screen readers get a "Refresh" action on the title and on every day instead of the gesture.
 */
@Composable
internal fun TodayScreen(
    state: TodayState,
    onIntent: (TodayIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = { onIntent(TodayIntent.Refresh) }
    val refreshAction = refreshAction(refresh)
    Scaffold(
        modifier = modifier,
        topBar = { TodayHeader(state.updatedAgo, refreshAction) },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        PullToRefreshBox(
            // While nothing is shown yet, the placeholders are the progress indicator.
            isRefreshing = state.isRefreshing && state.content != Content.Loading,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                Content.Loading -> LoadingDays()
                is Content.Empty -> EmptyContent(content.missing, onIntent)
                is Content.Error -> ErrorContent(content.message, refresh)
                is Content.Ready -> ReadyDays(content.days, state.notice, onIntent, refreshAction)
            }
        }
    }
}

/** Custom actions are offered on the item a screen reader has focused, so they go on focusable, merged nodes. */
@Composable
private fun refreshAction(refresh: () -> Unit): List<CustomAccessibilityAction> {
    val label = stringResource(Res.string.action_refresh)
    return remember(label, refresh) {
        listOf(
            CustomAccessibilityAction(label) {
                refresh()
                true
            },
        )
    }
}

@Composable
private fun TodayHeader(
    updatedAgo: Duration?,
    refreshAction: List<CustomAccessibilityAction>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {
                    heading()
                    customActions = refreshAction
                }.testTag("today.header"),
    ) {
        Text(
            text = stringResource(Res.string.today_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        updatedAgo?.let {
            Text(
                text = updatedText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyDays(
    days: List<DayUi>,
    notice: Notice?,
    onIntent: (TodayIntent) -> Unit,
    refreshAction: List<CustomAccessibilityAction>,
) {
    val onRefresh = { onIntent(TodayIntent.Refresh) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("today.days"),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        notice?.let { item(key = "notice") { NoticeBanner(it, onRefresh) } }
        // String keys: Android saves lazy-list keys in a Bundle, which cannot hold a LocalDate.
        items(days, key = { it.date.toString() }) { day ->
            DayCard(
                day = day,
                onOpenSpot = { spotId -> onIntent(TodayIntent.OpenSpot(spotId, day.date)) },
                onOfferRide = { offer -> onIntent(TodayIntent.OfferRide(offer)) },
                modifier = Modifier.semantics { customActions = refreshAction },
            )
        }
        item(key = "disclaimer") { Disclaimer(modifier = Modifier.padding(top = Spacing.s)) }
    }
}

@Composable
private fun NoticeBanner(
    notice: Notice,
    onRefresh: () -> Unit,
) {
    val text: StringResource =
        when (notice) {
            Notice.OFFLINE -> Res.string.banner_offline
            Notice.REFRESH_FAILED -> Res.string.banner_refresh_failed
            Notice.CREW_FAILED -> Res.string.banner_crew_failed
            Notice.STALE -> Res.string.banner_stale
        }
    StatusBanner(
        icon = if (notice == Notice.STALE) AppIcons.Clock else AppIcons.CloudOff,
        text = stringResource(text),
        modifier = Modifier.testTag("today.notice"),
        action = {
            ActionButton(
                text = stringResource(if (notice == Notice.STALE) Res.string.action_refresh else Res.string.action_retry),
                onClick = onRefresh,
                modifier = Modifier.testTag("today.retry"),
            )
        },
    )
}

@Composable
private fun LoadingDays() {
    val description = stringResource(Res.string.today_loading)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("today.loading").semantics { contentDescription = description },
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(DAYS_SHOWN) { Skeleton(modifier = Modifier.fillMaxWidth().height(Sizes.cardPlaceholder)) }
    }
}

@Composable
private fun EmptyContent(
    missing: Missing,
    onIntent: (TodayIntent) -> Unit,
) {
    // Scrollable, so the pull-to-refresh gesture works on this state too.
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("today.empty")) {
        when (missing) {
            Missing.PROFILE -> {
                MessageState(
                    icon = AppIcons.Person,
                    title = stringResource(Res.string.empty_profile_title),
                    body = stringResource(Res.string.empty_profile_body),
                    action = {
                        ActionButton(
                            text = stringResource(Res.string.action_set_up_profile),
                            onClick = { onIntent(TodayIntent.SetUpProfile) },
                            modifier = Modifier.testTag("today.setUpProfile"),
                        )
                    },
                )
            }

            Missing.FAVORITE_SPOTS -> {
                MessageState(
                    icon = AppIcons.Pin,
                    title = stringResource(Res.string.empty_spots_title),
                    body = stringResource(Res.string.empty_spots_body),
                    action = {
                        ActionButton(
                            text = stringResource(Res.string.action_pick_spots),
                            onClick = { onIntent(TodayIntent.PickSpots) },
                            modifier = Modifier.testTag("today.pickSpots"),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: StringResource,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("today.error")) {
        MessageState(
            icon = AppIcons.CloudOff,
            title = stringResource(Res.string.error_title),
            body = stringResource(message),
            action = {
                ActionButton(text = stringResource(Res.string.action_retry), onClick = onRetry, modifier = Modifier.testTag("today.retry"))
            },
        )
    }
}
