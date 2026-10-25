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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.ChoiceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.SectionTitle
import io.github.esseckers.pursuitofwind.core.designsystem.component.Skeleton
import io.github.esseckers.pursuitofwind.core.designsystem.component.StatusBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.TabTitle
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
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

// Parts of the Crew tab's content: the crew switch, the header with the invite, the members, and more crews.

/** One chip per crew; screen readers hear a group of radio buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewSwitch(
    crews: List<CrewChoice>,
    onChoose: (String) -> Unit,
) {
    val label = stringResource(Res.string.crew_switch)
    FlowRow(
        modifier = Modifier.semantics { contentDescription = label }.selectableGroup().testTag("crew.switch"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        crews.forEach { crew ->
            ChoiceChip(
                label = crew.name,
                selected = crew.selected,
                onClick = { onChoose(crew.id) },
                singleChoice = true,
                modifier = Modifier.testTag("crew.choose.${crew.id}"),
            )
        }
    }
}

@Composable
internal fun CrewHeader(
    crew: CrewUi,
    onShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = crew.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        IconLine(icon = AppIcons.Person, text = stringResource(Res.string.crew_invite_code, crew.inviteCode))
        OutlinedButton(onClick = onShare, modifier = Modifier.testTag("crew.share")) {
            Text(text = stringResource(Res.string.action_share_invite))
        }
    }
}

@Composable
internal fun Members(members: List<MemberUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("crew.members")) {
        SectionTitle(text = stringResource(Res.string.crew_members))
        members.forEach { member ->
            val name = if (member.you) stringResource(Res.string.crew_member_you, member.name) else member.name
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                if (member.owner) {
                    Text(
                        text = stringResource(Res.string.crew_member_owner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Another crew: always within reach, below everything else. */
@Composable
internal fun MoreCrews(
    onIntent: (CrewIntent) -> Unit,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        ActionButton(
            text = stringResource(Res.string.action_create_crew),
            onClick = { onIntent(CrewIntent.StartCreate) },
            modifier = Modifier.testTag("crew.create"),
        )
        ActionButton(
            text = stringResource(Res.string.action_join_crew),
            onClick = { onIntent(CrewIntent.StartJoin) },
            modifier = Modifier.testTag("crew.join"),
        )
    }
}
