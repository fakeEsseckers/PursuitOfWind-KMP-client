package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.DraftTextField
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.PrimaryButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.Stepper
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_cancel
import io.github.esseckers.pursuitofwind.core.resources.action_save
import io.github.esseckers.pursuitofwind.core.resources.spot_map_edit
import io.github.esseckers.pursuitofwind.core.resources.spot_map_follow
import io.github.esseckers.pursuitofwind.core.resources.spot_map_followed
import io.github.esseckers.pursuitofwind.core.resources.spot_map_hint
import io.github.esseckers.pursuitofwind.core.resources.spot_map_legend_danger
import io.github.esseckers.pursuitofwind.core.resources.spot_map_legend_ride
import io.github.esseckers.pursuitofwind.core.resources.spot_map_move_pin
import io.github.esseckers.pursuitofwind.core.resources.spot_map_name
import io.github.esseckers.pursuitofwind.core.resources.spot_map_nearby
import io.github.esseckers.pursuitofwind.core.resources.spot_map_new_here
import io.github.esseckers.pursuitofwind.core.resources.spot_map_tap_water
import io.github.esseckers.pursuitofwind.core.resources.spot_map_use_nearby
import io.github.esseckers.pursuitofwind.core.resources.spots_drive
import io.github.esseckers.pursuitofwind.core.resources.spots_drive_longer
import io.github.esseckers.pursuitofwind.core.resources.spots_drive_shorter
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.StepperUi
import io.github.esseckers.pursuitofwind.core.ui.durationText
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapIntent
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapPanel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.minutes

/** The card under the map: how to add a spot, a spot near the pin, the spot being placed, or the one tapped. */
@Composable
internal fun SpotMapPanelCard(
    panel: SpotMapPanel,
    onIntent: (SpotMapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth().testTag("spotmap.panel")) {
        Column(
            modifier = Modifier.padding(Spacing.m).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (panel) {
                SpotMapPanel.Hint -> HintPanel()
                is SpotMapPanel.Nearby -> NearbyPanel(panel, onIntent)
                is SpotMapPanel.Draft -> DraftPanel(panel, onIntent)
                is SpotMapPanel.Selected -> SelectedPanel(panel, onIntent)
            }
        }
    }
}

@Composable
private fun HintPanel() {
    IconLine(
        icon = AppIcons.Pin,
        text = stringResource(Res.string.spot_map_hint),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("spotmap.hint"),
    )
}

@Composable
private fun ColumnScope.NearbyPanel(
    panel: SpotMapPanel.Nearby,
    onIntent: (SpotMapIntent) -> Unit,
) {
    Text(
        text = stringResource(Res.string.spot_map_nearby, panel.name, panel.meters),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag("spotmap.nearby"),
    )
    PrimaryButton(
        text = stringResource(Res.string.spot_map_use_nearby, panel.name),
        onClick = { onIntent(SpotMapIntent.UseNearby) },
        modifier = Modifier.testTag("spotmap.useNearby"),
    )
    ActionButton(
        text = stringResource(Res.string.spot_map_new_here),
        onClick = { onIntent(SpotMapIntent.AddHere) },
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("spotmap.newHere"),
    )
}

@Composable
private fun ColumnScope.DraftPanel(
    panel: SpotMapPanel.Draft,
    onIntent: (SpotMapIntent) -> Unit,
) {
    val water = panel.water
    IconLine(
        icon = AppIcons.Wind,
        text = if (water == null) stringResource(Res.string.spot_map_tap_water) else stringResource(waterLabel(water)),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.testTag("spotmap.water"),
    )
    if (water != null) {
        ShoreLegend()
        Text(
            text = stringResource(Res.string.spot_map_move_pin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DraftTextField(
        value = panel.name,
        version = panel.nameVersion,
        onValueChange = { onIntent(SpotMapIntent.Rename(it)) },
        label = stringResource(Res.string.spot_map_name),
        accepts = { ApiLimits.lengthOf(it) <= ApiLimits.Spot.NAME_MAX_LENGTH },
        modifier = Modifier.testTag("spotmap.name"),
    )
    panel.drive?.let { drive -> DriveStepper(drive, name = panel.name, onIntent = onIntent) }
    panel.error?.let { SaveError(it) }
    PrimaryButton(
        text = stringResource(Res.string.action_save),
        onClick = { onIntent(SpotMapIntent.Save) },
        enabled = panel.canSave,
        busy = panel.saving,
        modifier = Modifier.testTag("spotmap.save"),
    )
    if (panel.cancellable) {
        ActionButton(
            text = stringResource(Res.string.action_cancel),
            onClick = { onIntent(SpotMapIntent.Cancel) },
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("spotmap.cancel"),
        )
    }
}

/** What the colours of the ring around the pin mean. */
@Composable
private fun ShoreLegend() {
    val verdicts = PursuitOfWindTheme.colors
    Column(modifier = Modifier.testTag("spotmap.legend"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendLine(color = verdicts.go.accent, text = stringResource(Res.string.spot_map_legend_ride))
        LegendLine(color = verdicts.noGo.accent, text = stringResource(Res.string.spot_map_legend_danger))
    }
}

@Composable
private fun LegendLine(
    color: Color,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(LEGEND_SWATCH).background(color, CircleShape))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ColumnScope.SelectedPanel(
    panel: SpotMapPanel.Selected,
    onIntent: (SpotMapIntent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = panel.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag("spotmap.selected"),
        )
        if (panel.mine) {
            ActionButton(
                text = stringResource(Res.string.spot_map_edit),
                onClick = { onIntent(SpotMapIntent.EditSelected) },
                modifier = Modifier.testTag("spotmap.edit"),
            )
        }
    }
    if (panel.followed) {
        IconLine(icon = AppIcons.Check, text = stringResource(Res.string.spot_map_followed), style = MaterialTheme.typography.bodyMedium)
    } else {
        DriveStepper(panel.drive, name = panel.name, onIntent = onIntent)
        panel.error?.let { SaveError(it) }
        PrimaryButton(
            text = stringResource(Res.string.spot_map_follow),
            onClick = { onIntent(SpotMapIntent.Follow) },
            busy = panel.saving,
            modifier = Modifier.testTag("spotmap.follow"),
        )
    }
}

@Composable
private fun DriveStepper(
    drive: StepperUi,
    name: String,
    onIntent: (SpotMapIntent) -> Unit,
) {
    Stepper(
        label = stringResource(Res.string.spots_drive),
        value = durationText(drive.value.minutes),
        decreaseLabel = stringResource(Res.string.spots_drive_shorter, name),
        increaseLabel = stringResource(Res.string.spots_drive_longer, name),
        onDecrease = { onIntent(SpotMapIntent.ChangeDrive(Step.DOWN)) },
        onIncrease = { onIntent(SpotMapIntent.ChangeDrive(Step.UP)) },
        canDecrease = drive.canDecrease,
        canIncrease = drive.canIncrease,
        modifier = Modifier.fillMaxWidth().testTag("spotmap.drive"),
    )
}

@Composable
private fun SaveError(message: StringResource) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("spotmap.error"),
    )
}

/** The colour dots of the legend, as wide as the ring is on the map. */
private val LEGEND_SWATCH = 14.dp
