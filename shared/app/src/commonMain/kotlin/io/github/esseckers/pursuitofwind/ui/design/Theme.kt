package io.github.esseckers.pursuitofwind.ui.design

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

/** Sizes that are not spacing: icons, borders and placeholders. */
internal object Sizes {
    val iconSmall = 16.dp
    val icon = 20.dp
    val iconLarge = 48.dp
    val border = 1.dp

    /** Height of a placeholder card while a day's answer loads. */
    val cardPlaceholder = 196.dp
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
