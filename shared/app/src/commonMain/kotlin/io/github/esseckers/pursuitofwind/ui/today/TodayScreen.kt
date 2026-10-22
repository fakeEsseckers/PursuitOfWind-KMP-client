package io.github.esseckers.pursuitofwind.ui.today

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
import io.github.esseckers.pursuitofwind.ui.design.ActionButton
import io.github.esseckers.pursuitofwind.ui.design.AppIcons
import io.github.esseckers.pursuitofwind.ui.design.Disclaimer
import io.github.esseckers.pursuitofwind.ui.design.MessageState
import io.github.esseckers.pursuitofwind.ui.design.Sizes
import io.github.esseckers.pursuitofwind.ui.design.Skeleton
import io.github.esseckers.pursuitofwind.ui.design.Spacing
import io.github.esseckers.pursuitofwind.ui.design.StatusBanner
import io.github.esseckers.pursuitofwind.ui.generated.resources.Res
import io.github.esseckers.pursuitofwind.ui.generated.resources.action_refresh
import io.github.esseckers.pursuitofwind.ui.generated.resources.action_retry
import io.github.esseckers.pursuitofwind.ui.generated.resources.banner_crew_failed
import io.github.esseckers.pursuitofwind.ui.generated.resources.banner_offline
import io.github.esseckers.pursuitofwind.ui.generated.resources.banner_refresh_failed
import io.github.esseckers.pursuitofwind.ui.generated.resources.banner_stale
import io.github.esseckers.pursuitofwind.ui.generated.resources.empty_profile_body
import io.github.esseckers.pursuitofwind.ui.generated.resources.empty_profile_title
import io.github.esseckers.pursuitofwind.ui.generated.resources.empty_spots_body
import io.github.esseckers.pursuitofwind.ui.generated.resources.empty_spots_title
import io.github.esseckers.pursuitofwind.ui.generated.resources.error_title
import io.github.esseckers.pursuitofwind.ui.generated.resources.today_loading
import io.github.esseckers.pursuitofwind.ui.generated.resources.today_title
import io.github.esseckers.pursuitofwind.ui.generated.resources.updated_days_ago
import io.github.esseckers.pursuitofwind.ui.generated.resources.updated_hours_ago
import io.github.esseckers.pursuitofwind.ui.generated.resources.updated_just_now
import io.github.esseckers.pursuitofwind.ui.generated.resources.updated_minutes_ago
import io.github.esseckers.pursuitofwind.ui.today.TodayUiState.Content
import io.github.esseckers.pursuitofwind.ui.today.TodayUiState.Missing
import io.github.esseckers.pursuitofwind.ui.today.TodayUiState.Notice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * "Go?": the answer for today and the next two days. Stateless; [TodayRoute] connects it to [TodayViewModel].
 * Pull to refresh; screen readers get a "Refresh" action on the title and on every day instead of the gesture.
 */
@Composable
internal fun TodayScreen(
    state: TodayUiState,
    onIntent: (TodayIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = { onIntent(TodayIntent.Refresh) }
    val refreshAction = refreshAction(refresh)
    Scaffold(modifier = modifier, topBar = { TodayHeader(state.updatedAgo, refreshAction) }) { padding ->
        PullToRefreshBox(
            // While nothing is shown yet, the placeholders are the progress indicator.
            isRefreshing = state.isRefreshing && state.content != Content.Loading,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                Content.Loading -> LoadingDays()
                is Content.Empty -> EmptyContent(content.missing)
                is Content.Error -> ErrorContent(content.message, refresh)
                is Content.Ready -> ReadyDays(content.days, state.notice, refresh, refreshAction)
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
private fun updatedText(age: Duration): String =
    when {
        age < 1.minutes -> stringResource(Res.string.updated_just_now)
        age < 1.hours -> age.inWholeMinutes.toInt().let { pluralStringResource(Res.plurals.updated_minutes_ago, it, it) }
        age < 1.days -> age.inWholeHours.toInt().let { pluralStringResource(Res.plurals.updated_hours_ago, it, it) }
        else -> age.inWholeDays.toInt().let { pluralStringResource(Res.plurals.updated_days_ago, it, it) }
    }

@Composable
private fun ReadyDays(
    days: List<DayUi>,
    notice: Notice?,
    onRefresh: () -> Unit,
    refreshAction: List<CustomAccessibilityAction>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("today.days"),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        notice?.let { item(key = "notice") { NoticeBanner(it, onRefresh) } }
        // String keys: Android saves lazy-list keys in a Bundle, which cannot hold a LocalDate.
        items(days, key = { it.date.toString() }) { day ->
            DayCard(day = day, modifier = Modifier.semantics { customActions = refreshAction })
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
private fun EmptyContent(missing: Missing) {
    // Scrollable, so the pull-to-refresh gesture works on this state too.
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("today.empty")) {
        when (missing) {
            Missing.PROFILE -> {
                MessageState(
                    icon = AppIcons.Person,
                    title = stringResource(Res.string.empty_profile_title),
                    body = stringResource(Res.string.empty_profile_body),
                )
            }

            Missing.FAVORITE_SPOTS -> {
                MessageState(
                    icon = AppIcons.Pin,
                    title = stringResource(Res.string.empty_spots_title),
                    body = stringResource(Res.string.empty_spots_body),
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
