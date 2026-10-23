package io.github.esseckers.pursuitofwind.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing

/** The title of a group of fields; a heading, so screen readers can jump between groups. */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/**
 * A text field whose text lives in a ViewModel's state. The field keeps its own text and cursor, so typing never waits
 * for the state to come back; it starts over from [value] whenever [version] changes (the form was filled or rewritten
 * from outside). Text that [accepts] rejects is neither shown nor sent. [error] replaces the hint below the field.
 */
@Composable
internal fun DraftTextField(
    value: String,
    version: Int,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    accepts: (String) -> Boolean = { true },
    error: String? = null,
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var field by remember(version) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            if (accepts(next.text)) {
                val changed = next.text != field.text
                field = next
                if (changed) onValueChange(next.text)
            }
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A chip that is on or off, with a check mark while on. Screen readers hear a checkbox for one of a set (kites), or a
 * radio button with [singleChoice] for one of several (level, unit); put those in a `selectableGroup`.
 */
@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleChoice: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        // Outer semantics win over the chip's own checkbox role.
        modifier = if (singleChoice) modifier.semantics { role = Role.RadioButton } else modifier,
        leadingIcon =
            if (selected) {
                { Icon(imageVector = AppIcons.Check, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) }
            } else {
                null
            },
    )
}

/**
 * A value changed in steps with minus and plus, such as a drive time. [value] is announced when it changes;
 * [decreaseLabel] and [increaseLabel] are what screen readers say for the buttons.
 */
@Composable
internal fun Stepper(
    label: String,
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(imageVector = AppIcons.Minus, contentDescription = decreaseLabel, modifier = Modifier.size(Sizes.icon))
        }
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(imageVector = AppIcons.Plus, contentDescription = increaseLabel, modifier = Modifier.size(Sizes.icon))
        }
    }
}

/** The main action of a screen, full width. While [busy], it shows progress and takes no taps. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Sizes.iconSmall),
                    color = LocalContentColor.current,
                    strokeWidth = Sizes.progressStroke,
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
