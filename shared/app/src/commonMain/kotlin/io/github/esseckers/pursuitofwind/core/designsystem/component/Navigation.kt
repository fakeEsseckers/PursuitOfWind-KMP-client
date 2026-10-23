package io.github.esseckers.pursuitofwind.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing

/**
 * The bar above a screen that was opened from another one: back, the title with an optional [subtitle], and [actions].
 * The title is a heading; the bar leaves room for the status bar and display cutouts.
 */
@Composable
internal fun ScreenTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIconButton(icon = AppIcons.Back, label = backLabel, onClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs).semantics(mergeDescendants = true) { heading() }) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** An icon button for a top bar; [label] is what screen readers say. */
@Composable
internal fun BarIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(Sizes.icon))
    }
}

/**
 * One line that opens something else, with a chevron at the end. Screen readers read [text] and offer [onClickLabel]
 * as the action.
 */
@Composable
internal fun LinkRow(
    text: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = style, modifier = Modifier.weight(1f))
        Icon(
            imageVector = AppIcons.Forward,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(Sizes.iconSmall),
        )
    }
}
