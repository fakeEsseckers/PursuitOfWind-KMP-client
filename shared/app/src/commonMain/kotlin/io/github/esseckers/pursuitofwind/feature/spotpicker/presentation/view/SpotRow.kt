package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.Stepper
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.spots_drive
import io.github.esseckers.pursuitofwind.core.resources.spots_drive_longer
import io.github.esseckers.pursuitofwind.core.resources.spots_drive_shorter
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.durationText
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickSpotUi
import org.jetbrains.compose.resources.stringResource

/**
 * One spot of the catalog: a checkbox row that makes it a favorite, and, once chosen, the drive from home. The row and
 * the drive buttons are separate nodes for screen readers: "Nordstrand Bay, checked", then "Longer drive to …".
 */
@Composable
internal fun SpotRow(
    spot: PickSpotUi,
    onToggle: () -> Unit,
    onDrive: (Step) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = if (spot.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(modifier = modifier.fillMaxWidth().testTag("spots.spot.${spot.id.value}"), border = BorderStroke(Sizes.border, outline)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .toggleable(value = spot.selected, role = Role.Checkbox, onValueChange = { onToggle() })
                    .padding(start = Spacing.xs, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs)
                    .testTag("spots.toggle.${spot.id.value}"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The row handles the tap, so the box itself is only a picture of the state.
            Checkbox(checked = spot.selected, onCheckedChange = null, modifier = Modifier.padding(Spacing.s))
            Text(text = spot.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (spot.selected) {
            Stepper(
                label = stringResource(Res.string.spots_drive),
                value = durationText(spot.drive),
                decreaseLabel = stringResource(Res.string.spots_drive_shorter, spot.name),
                increaseLabel = stringResource(Res.string.spots_drive_longer, spot.name),
                onDecrease = { onDrive(Step.DOWN) },
                onIncrease = { onDrive(Step.UP) },
                canDecrease = spot.canShorten,
                canIncrease = spot.canLengthen,
                modifier =
                    Modifier
                        .padding(
                            start = Spacing.m,
                            end = Spacing.xs,
                            bottom = Spacing.xs,
                        ).testTag("spots.drive.${spot.id.value}"),
            )
        }
    }
}
