package io.github.esseckers.pursuitofwind.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.confidence_high
import io.github.esseckers.pursuitofwind.core.resources.confidence_low
import io.github.esseckers.pursuitofwind.core.resources.confidence_medium
import io.github.esseckers.pursuitofwind.core.resources.confidence_unknown
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** "High confidence" and so on. */
internal fun confidenceLabel(confidence: Confidence): StringResource =
    when (confidence) {
        Confidence.HIGH -> Res.string.confidence_high
        Confidence.MEDIUM -> Res.string.confidence_medium
        Confidence.LOW -> Res.string.confidence_low
        Confidence.UNKNOWN -> Res.string.confidence_unknown
    }

@Composable
@ReadOnlyComposable
private fun confidenceColor(confidence: Confidence): Color =
    when (confidence) {
        Confidence.HIGH -> PursuitOfWindTheme.colors.confidenceHigh
        Confidence.MEDIUM -> PursuitOfWindTheme.colors.confidenceMedium
        Confidence.LOW -> PursuitOfWindTheme.colors.confidenceLow
        Confidence.UNKNOWN -> PursuitOfWindTheme.colors.confidenceUnknown
    }

/** How many of the three bars are filled: the shape tells the level without its color. */
private fun Confidence.filledBars(): Int =
    when (this) {
        Confidence.HIGH -> BAR_COUNT
        Confidence.MEDIUM -> 2
        Confidence.LOW -> 1
        Confidence.UNKNOWN -> 0
    }

/** A pill with signal bars and the confidence level in words. */
@Composable
internal fun ConfidenceChip(
    confidence: Confidence,
    modifier: Modifier = Modifier,
) {
    val color = confidenceColor(confidence)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = CHIP_SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(Sizes.border, color),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s + Spacing.xs, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConfidenceBars(filled = confidence.filledBars(), color = color, modifier = Modifier.size(Sizes.iconSmall))
            Text(text = stringResource(confidenceLabel(confidence)), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Three rising bars, [filled] of them solid; the others are drawn faintly. Decorative: the label says the level. */
@Composable
private fun ConfidenceBars(
    filled: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val gap = size.width / BAR_GAP_DIVISOR
        val barWidth = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
        repeat(BAR_COUNT) { index ->
            val height = size.height * (index + 1) / BAR_COUNT
            drawRoundRect(
                color = if (index < filled) color else color.copy(alpha = EMPTY_BAR_ALPHA),
                topLeft = Offset(x = index * (barWidth + gap), y = size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private const val BAR_COUNT = 3
private const val BAR_GAP_DIVISOR = 6f
private const val EMPTY_BAR_ALPHA = 0.25f
private const val CHIP_SURFACE_ALPHA = 0.6f
