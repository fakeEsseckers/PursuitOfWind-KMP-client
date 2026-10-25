package io.github.esseckers.pursuitofwind.feature.crew.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.AppSnackbarHost
import io.github.esseckers.pursuitofwind.core.designsystem.component.ChoiceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.RefreshNoticeBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.SectionTitle
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.TabTitle
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_create_crew
import io.github.esseckers.pursuitofwind.core.resources.action_join_crew
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.action_share_invite
import io.github.esseckers.pursuitofwind.core.resources.banner_crew_offline
import io.github.esseckers.pursuitofwind.core.resources.banner_crew_refresh_failed
import io.github.esseckers.pursuitofwind.core.resources.crew_error_title
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_code
import io.github.esseckers.pursuitofwind.core.resources.crew_loading
import io.github.esseckers.pursuitofwind.core.resources.crew_member_owner
import io.github.esseckers.pursuitofwind.core.resources.crew_member_you
import io.github.esseckers.pursuitofwind.core.resources.crew_members
import io.github.esseckers.pursuitofwind.core.resources.crew_no_rides
import io.github.esseckers.pursuitofwind.core.resources.crew_none_body
import io.github.esseckers.pursuitofwind.core.resources.crew_none_title
import io.github.esseckers.pursuitofwind.core.resources.crew_rides
import io.github.esseckers.pursuitofwind.core.resources.crew_switch
import io.github.esseckers.pursuitofwind.core.resources.tab_crew
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewChoice
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState.Content
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.MemberUi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Crew tab: the crew on screen with its invite code, its rides by day with a seat to take, and its members; chips
 * to switch crews, and ways to create or join one. Stateless; `CrewRoute` connects it to its ViewModel and shows
 * [snackbarHostState]'s messages. Pull to refresh; screen readers get a "Refresh" action on the title instead.
 */
@Composable
internal fun CrewScreen(
    state: CrewState,
    onIntent: (CrewIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val refresh = { onIntent(CrewIntent.Refresh) }
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
        topBar = { TabTitle(title = stringResource(Res.string.tab_crew), modifier = Modifier.semantics { customActions = refreshAction }) },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.content != Content.Loading,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val content = state.content) {
                Content.Loading -> LoadingCrew()
                Content.NoCrew -> NoCrew(onIntent)
                is Content.Error -> CrewError(content.message, refresh)
                is Content.Ready -> CrewContent(content.crew, state.crews, state.notice, onIntent)
            }
        }
    }
    CrewDialogs(dialog = state.dialog, onIntent = onIntent)
}

@Composable
private fun CrewContent(
    crew: CrewUi,
    crews: List<CrewChoice>,
    notice: RefreshNotice?,
    onIntent: (CrewIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxSize().testTag("crew.list"),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        notice?.let {
            item(key = "notice") {
                RefreshNoticeBanner(
                    notice = it,
                    offline = Res.string.banner_crew_offline,
                    failed = Res.string.banner_crew_refresh_failed,
                    tagPrefix = "crew",
                    onRetry = { onIntent(CrewIntent.Refresh) },
                )
            }
        }
        if (crews.size > 1) item(key = "switch") { CrewSwitch(crews) { onIntent(CrewIntent.ChooseCrew(it)) } }
        item(key = "header") { CrewHeader(crew) { onIntent(CrewIntent.ShareInvite) } }
        rides(crew, onIntent)
        item(key = "members") { Members(crew.members) }
        item(key = "more") { MoreCrews(onIntent) }
    }
}

private fun LazyListScope.rides(
    crew: CrewUi,
    onIntent: (CrewIntent) -> Unit,
) {
    item(key = "rides") { SectionTitle(text = stringResource(Res.string.crew_rides)) }
    if (crew.days.isEmpty()) {
        item(key = "noRides") {
            Text(
                text = stringResource(Res.string.crew_no_rides),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("crew.noRides"),
            )
        }
    }
    crew.days.forEach { day ->
        item(key = "day.${day.date}") {
            Text(
                text = dayLabel(day.label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }
        items(day.rides, key = { "ride.${it.id}" }) { ride ->
            CrewRideCard(
                ride = ride,
                onAction = { action ->
                    val intent =
                        when (action) {
                            RideActionEntity.TAKE_SEAT -> CrewIntent.TakeSeat(ride.id)
                            RideActionEntity.LEAVE -> CrewIntent.LeaveRide(ride.id)
                            RideActionEntity.CANCEL -> CrewIntent.CancelRide(ride.id)
                        }
                    onIntent(intent)
                },
                onOpenSpot = { onIntent(CrewIntent.OpenSpot(ride.spotId, ride.date)) },
            )
        }
    }
}

@Composable
private fun LoadingCrew() {
    val description = stringResource(Res.string.crew_loading)
    Column(
        modifier =
            Modifier
                .widthIn(max = Sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(Spacing.m)
                .semantics { contentDescription = description }
                .testTag("crew.loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        repeat(PLACEHOLDER_CARDS) { Skeleton(modifier = Modifier.fillMaxWidth().height(Sizes.cardPlaceholder)) }
    }
}

@Composable
private fun NoCrew(onIntent: (CrewIntent) -> Unit) {
    // Scrollable, so the pull-to-refresh gesture works on this state too.
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("crew.none")) {
        MessageState(
            icon = AppIcons.Car,
            title = stringResource(Res.string.crew_none_title),
            body = stringResource(Res.string.crew_none_body),
            action = { MoreCrews(onIntent, horizontalAlignment = Alignment.CenterHorizontally) },
        )
    }
}

@Composable
private fun CrewError(
    message: StringResource,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("crew.error")) {
        MessageState(
            icon = AppIcons.CloudOff,
            title = stringResource(Res.string.crew_error_title),
            body = stringResource(message),
            action = {
                ActionButton(text = stringResource(Res.string.action_retry), onClick = onRetry, modifier = Modifier.testTag("crew.retry"))
            },
        )
    }
}

private const val PLACEHOLDER_CARDS = 2
