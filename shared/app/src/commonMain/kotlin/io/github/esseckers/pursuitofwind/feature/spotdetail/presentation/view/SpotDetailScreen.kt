package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.BarIconButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.Disclaimer
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.NavigationBarScrim
import io.github.esseckers.pursuitofwind.core.designsystem.component.RefreshNoticeBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.ScreenTopBar
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.StatusBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.contentEndInsets
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_back
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.banner_offline
import io.github.esseckers.pursuitofwind.core.resources.banner_refresh_failed
import io.github.esseckers.pursuitofwind.core.resources.chart_legend
import io.github.esseckers.pursuitofwind.core.resources.chart_title
import io.github.esseckers.pursuitofwind.core.resources.empty_profile_body
import io.github.esseckers.pursuitofwind.core.resources.empty_profile_title
import io.github.esseckers.pursuitofwind.core.resources.error_title
import io.github.esseckers.pursuitofwind.core.resources.spot_loading
import io.github.esseckers.pursuitofwind.core.resources.spot_missing_body
import io.github.esseckers.pursuitofwind.core.resources.spot_missing_title
import io.github.esseckers.pursuitofwind.core.ui.driveText
import io.github.esseckers.pursuitofwind.core.ui.updatedText
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailIntent
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Missing
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotHeaderUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.WindowUi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * One spot, hour by hour. Stateless; `SpotDetailRoute` connects it to its ViewModel. The top bar has a back button and
 * a refresh button, so neither gesture (system back, pull to refresh) is the only way.
 */
@Composable
internal fun SpotDetailScreen(
    state: SpotDetailState,
    onIntent: (SpotDetailIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = { onIntent(SpotDetailIntent.Refresh) }
    Scaffold(
        modifier = modifier,
        topBar = { SpotTopBar(state.header, state.updatedAgo, onBack, refresh) },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        PullToRefreshBox(
            // While nothing is shown yet, the placeholders are the progress indicator.
            isRefreshing = state.isRefreshing && state.content != Content.Loading,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                Content.Loading -> LoadingChart()
                is Content.Missing -> MissingContent(content.missing)
                is Content.Error -> ErrorContent(content.message, refresh)
                is Content.Ready -> ReadyContent(content, state.notice, onIntent)
            }
            NavigationBarScrim(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SpotTopBar(
    header: SpotHeaderUi?,
    updatedAgo: Duration?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val facts = listOfNotNull(header?.let { driveText(it.drive) }, updatedAgo?.let { updatedText(it) })
    ScreenTopBar(
        title = header?.name.orEmpty(),
        subtitle = facts.joinToString(" · ").ifEmpty { null },
        backLabel = stringResource(Res.string.action_back),
        onBack = onBack,
        modifier = Modifier.testTag("spot.header"),
    ) {
        BarIconButton(
            icon = AppIcons.Refresh,
            label = stringResource(Res.string.action_refresh),
            onClick = onRefresh,
            modifier = Modifier.testTag("spot.refresh"),
        )
    }
}

@Composable
private fun ReadyContent(
    content: Content.Ready,
    notice: RefreshNotice?,
    onIntent: (SpotDetailIntent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Scrolls behind the navigation bar and ends clear of it.
                .windowInsetsPadding(contentEndInsets())
                .padding(vertical = Spacing.m)
                .testTag("spot.ready"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val inset = Modifier.padding(horizontal = Spacing.m)
        Banner(notice, onRetry = { onIntent(SpotDetailIntent.Refresh) }, modifier = inset)
        Text(
            text = stringResource(Res.string.chart_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = inset.semantics { heading() },
        )
        // Counts taps on days and windows, so one on the day or window already shown still brings it back into view.
        var jumps by remember { mutableIntStateOf(0) }
        val chart = content.chart
        DayChips(
            days = content.days,
            selectedHour = content.selected.index,
            onSelectDay = {
                jumps++
                onIntent(SpotDetailIntent.SelectDay(it.date))
            },
            modifier = inset,
        )
        WindChart(
            chart = chart,
            days = content.days,
            selected = content.selected.index,
            nowOffset = content.nowOffset,
            jumps = jumps,
            onSelectHour = { onIntent(SpotDetailIntent.SelectHour(chart.hours[it].start)) },
            modifier = Modifier.padding(end = Spacing.m),
        )
        Text(
            text = stringResource(Res.string.chart_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = inset,
        )
        DetailsAndWindows(
            content = content,
            onShowWindow = {
                jumps++
                onIntent(SpotDetailIntent.SelectHour(chart.hours[it.firstHour].start))
            },
            modifier = inset,
        )
        Disclaimer(modifier = inset.padding(top = Spacing.s))
    }
}

/** The selected hour's details and the windows: side by side on a wide window, one under the other on a phone. */
@Composable
private fun DetailsAndWindows(
    content: Content.Ready,
    onShowWindow: (WindowUi) -> Unit,
    modifier: Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val half = Modifier.weight(1f).widthIn(min = Sizes.detailsMinWidth)
        HourDetails(details = content.selected, modifier = half)
        WindowList(windows = content.windows, onShowWindow = onShowWindow, modifier = half)
    }
}

/** Why the chart may not be current. */
@Composable
private fun Banner(
    notice: RefreshNotice?,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    notice?.let {
        RefreshNoticeBanner(
            notice = it,
            offline = Res.string.banner_offline,
            failed = Res.string.banner_refresh_failed,
            tagPrefix = "spot",
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingChart() {
    val description = stringResource(Res.string.spot_loading)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.m)
                .testTag("spot.loading")
                .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Skeleton(modifier = Modifier.fillMaxWidth().height(Sizes.chartPlaceholder))
        Skeleton(modifier = Modifier.fillMaxWidth().height(Sizes.cardPlaceholder))
    }
}

@Composable
private fun MissingContent(missing: Missing) {
    // Scrollable, so the pull-to-refresh gesture works on this state too.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).windowInsetsPadding(contentEndInsets())
                .testTag("spot.missing"),
    ) {
        when (missing) {
            Missing.PROFILE -> {
                MessageState(
                    icon = AppIcons.Person,
                    title = stringResource(Res.string.empty_profile_title),
                    body = stringResource(Res.string.empty_profile_body),
                )
            }

            Missing.SPOT -> {
                MessageState(
                    icon = AppIcons.Pin,
                    title = stringResource(Res.string.spot_missing_title),
                    body = stringResource(Res.string.spot_missing_body),
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
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).windowInsetsPadding(contentEndInsets())
                .testTag("spot.error"),
    ) {
        MessageState(
            icon = AppIcons.CloudOff,
            title = stringResource(Res.string.error_title),
            body = stringResource(message),
            action = {
                ActionButton(text = stringResource(Res.string.action_retry), onClick = onRetry, modifier = Modifier.testTag("spot.retry"))
            },
        )
    }
}
