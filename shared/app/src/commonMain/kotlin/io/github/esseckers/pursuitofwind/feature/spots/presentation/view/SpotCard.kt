package io.github.esseckers.pursuitofwind.feature.spots.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.LinkRow
import io.github.esseckers.pursuitofwind.core.designsystem.component.Stepper
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_remove
import io.github.esseckers.pursuitofwind.core.resources.my_spots_favorite
import io.github.esseckers.pursuitofwind.core.resources.my_spots_favorite_hint
import io.github.esseckers.pursuitofwind.core.resources.my_spots_forecast
import io.github.esseckers.pursuitofwind.core.resources.my_spots_remove
import io.github.esseckers.pursuitofwind.core.resources.my_spots_restoring
import io.github.esseckers.pursuitofwind.core.resources.my_spots_saving
import io.github.esseckers.pursuitofwind.core.resources.open_spot
import io.github.esseckers.pursuitofwind.core.resources.spots_drive
import io.github.esseckers.pursuitofwind.core.resources.spots_drive_longer
import io.github.esseckers.pursuitofwind.core.resources.spots_drive_shorter
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.durationText
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.MySpotUi
import org.jetbrains.compose.resources.stringResource

/**
 * One followed spot: its name as a heading, whether its sectors are checked, a favorite switch, the drive from home,
 * and links to its forecast and to remove it. Each control is its own node for screen readers, named after the spot
 * where it would otherwise be ambiguous.
 */
@Composable
internal fun SpotCard(
    spot: MySpotUi,
    onToggleFavorite: () -> Unit,
    onDrive: (Step) -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth().testTag("myspots.card.${spot.id.value}")) {
        Column(
            modifier = Modifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.m, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(text = spot.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            FavoriteRow(favorite = spot.favorite, onToggle = onToggleFavorite, tag = "myspots.favorite.${spot.id.value}")
            Stepper(
                label = stringResource(Res.string.spots_drive),
                value = durationText(spot.drive),
                decreaseLabel = stringResource(Res.string.spots_drive_shorter, spot.name),
                increaseLabel = stringResource(Res.string.spots_drive_longer, spot.name),
                onDecrease = { onDrive(Step.DOWN) },
                onIncrease = { onDrive(Step.UP) },
                canDecrease = spot.canShorten,
                canIncrease = spot.canLengthen,
                modifier = Modifier.testTag("myspots.drive.${spot.id.value}"),
            )
            if (spot.restoring || spot.saving) {
                Text(
                    text = stringResource(if (spot.restoring) Res.string.my_spots_restoring else Res.string.my_spots_saving),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("myspots.saving.${spot.id.value}"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                LinkRow(
                    text = stringResource(Res.string.my_spots_forecast),
                    onClickLabel = stringResource(Res.string.open_spot, spot.name),
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).testTag("myspots.open.${spot.id.value}"),
                )
                val removeLabel = stringResource(Res.string.my_spots_remove, spot.name)
                ActionButton(
                    text = stringResource(Res.string.action_remove),
                    onClick = onRemove,
                    modifier = Modifier.semantics { contentDescription = removeLabel }.testTag("myspots.remove.${spot.id.value}"),
                )
            }
        }
    }
}

/** "Favorite" with what it means, and a switch; the whole row toggles it. */
@Composable
private fun FavoriteRow(
    favorite: Boolean,
    onToggle: () -> Unit,
    tag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .toggleable(value = favorite, role = Role.Switch, onValueChange = { onToggle() })
                .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(Res.string.my_spots_favorite), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(Res.string.my_spots_favorite_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row handles the tap, so the switch itself is only a picture of the state.
        Switch(checked = favorite, onCheckedChange = null, modifier = Modifier.padding(end = Spacing.s))
    }
}
