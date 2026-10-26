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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.Disclaimer
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.RefreshNoticeBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.StatusBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_pick_spots
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.action_set_up_profile
import io.github.esseckers.pursuitofwind.core.resources.banner_crew_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_offline
import io.github.esseckers.pursuitofwind.core.resources.banner_refresh_failed
import io.github.esseckers.pursuitofwind.core.resources.empty_profile_body
import io.github.esseckers.pursuitofwind.core.resources.empty_profile_title
import io.github.esseckers.pursuitofwind.core.resources.empty_spots_body
import io.github.esseckers.pursuitofwind.core.resources.empty_spots_title
import io.github.esseckers.pursuitofwind.core.resources.error_title
import io.github.esseckers.pursuitofwind.core.resources.today_loading
import io.github.esseckers.pursuitofwind.core.resources.today_title
import io.github.esseckers.pursuitofwind.core.ui.updatedText
import io.github.esseckers.pursuitofwind.feature.today.domain.mapper.DAYS_SHOWN
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Missing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * "Go?": the answer for today and the next two days, one under the other on a phone and side by side on a wider window.
 * Stateless; `TodayRoute` connects it to [TodayViewModel]. Pull to refresh; screen readers get a "Refresh" action on the
 * title and on every day instead of the gesture.
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
                is Content.Ready -> ReadyDays(content.days, state.notice, state.crewFailed, onIntent, refreshAction)
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
    notice: RefreshNotice?,
    crewFailed: Boolean,
    onIntent: (TodayIntent) -> Unit,
    refreshAction: List<CustomAccessibilityAction>,
) {
    val onRefresh = { onIntent(TodayIntent.Refresh) }
    LazyVerticalGrid(
        columns = DayColumns,
        modifier = Modifier.fillMaxSize().testTag("today.days"),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (notice != null) {
            item(key = "notice", span = FullWidth) {
                RefreshNoticeBanner(
                    notice = notice,
                    offline = Res.string.banner_offline,
                    failed = Res.string.banner_refresh_failed,
                    tagPrefix = "today",
                    onRetry = onRefresh,
                    modifier = Modifier.readableWidth(),
                )
            }
        } else if (crewFailed) {
            item(key = "notice", span = FullWidth) { CrewFailedBanner(onRefresh, Modifier.readableWidth()) }
        }
        // String keys: Android saves lazy-list keys in a Bundle, which cannot hold a LocalDate.
        items(days, key = { it.date.toString() }) { day ->
            DayCard(
                day = day,
                onOpenSpot = { spotId -> onIntent(TodayIntent.OpenSpot(spotId, day.date)) },
                onOfferRide = { offer -> onIntent(TodayIntent.OfferRide(offer)) },
                modifier = Modifier.semantics { customActions = refreshAction },
            )
        }
        item(key = "disclaimer", span = FullWidth) {
            Disclaimer(modifier = Modifier.readableWidth().padding(top = Spacing.s))
        }
    }
}

/** The forecast is current, but the crew's rides could not be updated. */
@Composable
private fun CrewFailedBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusBanner(
        icon = AppIcons.CloudOff,
        text = stringResource(Res.string.banner_crew_failed),
        modifier = modifier.testTag("today.notice"),
        action = {
            ActionButton(text = stringResource(Res.string.action_retry), onClick = onRetry, modifier = Modifier.testTag("today.retry"))
        },
    )
}

@Composable
private fun LoadingDays() {
    val description = stringResource(Res.string.today_loading)
    LazyVerticalGrid(
        columns = DayColumns,
        modifier = Modifier.fillMaxSize().testTag("today.loading").semantics { contentDescription = description },
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
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

/**
 * All the days in a row when they fit at [Sizes.dayCardMinWidth] (a tablet in landscape, a desktop window), one under
 * the other otherwise. Never two in a row and one below: the days read as a sequence, and so does the grid.
 */
private object DayColumns : GridCells {
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> {
        val inRow = availableSize >= DAYS_SHOWN * Sizes.dayCardMinWidth.roundToPx() + (DAYS_SHOWN - 1) * spacing
        val columns = if (inRow) DAYS_SHOWN else 1
        val space = availableSize - (columns - 1) * spacing
        return List(columns) { column -> space / columns + if (column < space % columns) 1 else 0 }
    }
}

/** Banners and the disclaimer span every column. */
private val FullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/**
 * A full-width grid item that stops at [Sizes.contentMaxWidth], so its lines stay readable on a wide window. A grid
 * measures its items at the exact width of their cells, so the width is let go first.
 */
private fun Modifier.readableWidth(): Modifier = wrapContentWidth(Alignment.Start).widthIn(max = Sizes.contentMaxWidth)
