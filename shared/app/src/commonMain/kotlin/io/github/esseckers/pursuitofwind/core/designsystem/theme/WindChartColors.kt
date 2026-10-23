package io.github.esseckers.pursuitofwind.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The wind chart's colors. Bars take the verdict accents, which keep at least 3:1 against the background in both
 * themes, as graphics need; everything behind the bars is a faint tint, so it never competes with them.
 */
@Immutable
internal data class WindChartColors(
    val go: Color,
    val marginal: Color,
    val noGo: Color,
    val text: Color,
    val grid: Color,
    val daySeparator: Color,
    val night: Color,
    val windowGo: Color,
    val windowShort: Color,
    val gust: Color,
    val spreadFill: Color,
    val spreadEdge: Color,
    val arrow: Color,
    val now: Color,
    val selection: Color,
)

/** The chart's colors in the current theme. */
@Composable
@ReadOnlyComposable
internal fun windChartColors(): WindChartColors {
    val scheme = MaterialTheme.colorScheme
    val semantic = PursuitOfWindTheme.colors
    return WindChartColors(
        go = semantic.go.accent,
        marginal = semantic.marginal.accent,
        noGo = semantic.noGo.accent,
        text = scheme.onSurfaceVariant,
        grid = scheme.outlineVariant,
        daySeparator = scheme.outline,
        night = scheme.onSurface.copy(alpha = 0.07f),
        windowGo = semantic.go.accent.copy(alpha = 0.16f),
        windowShort = semantic.marginal.accent.copy(alpha = 0.12f),
        gust = scheme.onSurface,
        spreadFill = scheme.primary.copy(alpha = 0.18f),
        spreadEdge = scheme.primary.copy(alpha = 0.6f),
        arrow = scheme.onSurfaceVariant,
        now = scheme.primary,
        selection = scheme.onSurface,
    )
}
