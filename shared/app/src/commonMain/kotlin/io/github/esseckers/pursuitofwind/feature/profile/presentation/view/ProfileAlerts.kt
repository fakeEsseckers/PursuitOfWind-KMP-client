package io.github.esseckers.pursuitofwind.feature.profile.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.SectionTitle
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_open_settings
import io.github.esseckers.pursuitofwind.core.resources.profile_alerts
import io.github.esseckers.pursuitofwind.core.resources.profile_alerts_blocked
import io.github.esseckers.pursuitofwind.core.resources.profile_alerts_hint
import io.github.esseckers.pursuitofwind.core.resources.profile_alerts_switch
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.AlertsUi
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the app tells the rider about a new session window while it is closed. Like the language, it applies at
 * once, without saving. The whole row is the switch, so the label is its name for screen readers and the target is as
 * wide as the form. When the system does not show the app's notifications, the reason and the way to its settings
 * follow, announced as they appear.
 */
@Composable
internal fun AlertsChoice(
    alerts: AlertsUi,
    onSet: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle(text = stringResource(Res.string.profile_alerts))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(value = alerts.on, role = Role.Switch, onValueChange = onSet)
                    .testTag("profile.alerts"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.profile_alerts_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // The row handles the tap; the switch only shows the state.
            Switch(checked = alerts.on, onCheckedChange = null)
        }
        Hint(text = stringResource(Res.string.profile_alerts_hint))
        if (alerts.blocked) {
            Text(
                text = stringResource(Res.string.profile_alerts_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("profile.alerts.blocked"),
            )
            ActionButton(
                text = stringResource(Res.string.action_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("profile.alerts.settings"),
            )
        }
    }
}
