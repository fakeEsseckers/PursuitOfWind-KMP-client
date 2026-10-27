package io.github.esseckers.pursuitofwind.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Design-system entry point. Colors, typography, shapes and spacing live in this package only; screens use Material's
 * roles through [MaterialTheme] and the verdict and confidence colors through [PursuitOfWindTheme.colors].
 */
@Composable
internal fun PursuitOfWindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

internal object PursuitOfWindTheme {
    /** Verdict, confidence and placeholder colors of the current theme. */
    val colors: SemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticColors.current
}

/** Spacing tokens. Use these instead of raw dp values in screens. */
internal object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

/** Sizes that are not spacing: icons, borders, touch targets, the wind chart and placeholders. */
internal object Sizes {
    val iconSmall = 16.dp
    val icon = 20.dp
    val iconLarge = 48.dp
    val border = 1.dp

    /** Minimum width and height of anything that can be tapped. */
    val touchTarget = 48.dp

    /** Stroke of a small progress indicator inside a button. */
    val progressStroke = 2.dp

    /** Widest a form or a list of choices grows on a tablet or in landscape, so lines stay readable. */
    val contentMaxWidth = 600.dp

    /** Height of a placeholder row while a list of spots loads. */
    val rowPlaceholder = 64.dp

    /** Room left below a list for a floating action button, so the last item can scroll above it. */
    val fabClearance = 88.dp

    /** Height of a placeholder card while a day's answer loads. */
    val cardPlaceholder = 196.dp

    /** The narrowest a day card on Today gets in a row of days; narrower, the days go one under the other. */
    val dayCardMinWidth = 280.dp

    /** The list pane beside a spot's forecast on a wide window: a phone's width of list plus the 96 dp tab rail. */
    val listPaneWidth = 456.dp

    /** Width of one hour on the wind chart: wide enough to tap, 72 hours scroll sideways. */
    val chartHour = 28.dp

    /** Height of the wind chart's bars area. */
    val chartPlot = 168.dp

    /** Height of the wind chart's lane of direction arrows. */
    val chartArrowLane = 28.dp

    /** Height of the placeholder while the wind chart loads. */
    val chartPlaceholder = 280.dp
}

// Material's type scale with heavier titles, so a verdict and a spot name read at a glance.
private val Base = Typography()

private val AppTypography =
    Base.copy(
        headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )

private val AppShapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
    )
