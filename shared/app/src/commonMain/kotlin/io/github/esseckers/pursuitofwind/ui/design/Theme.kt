package io.github.esseckers.pursuitofwind.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Ocean blue primary, sea green secondary. Container and variant roles are set explicitly so nothing
// falls back to Material's baseline palette. The full design system arrives with the Today screen (Issue #7).
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0B5C8A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCCE6FF),
        onPrimaryContainer = Color(0xFF001E31),
        secondary = Color(0xFF00897B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFB2F0E6),
        onSecondaryContainer = Color(0xFF00201C),
        background = Color(0xFFF7FAFC),
        onBackground = Color(0xFF171C20),
        surface = Color(0xFFF7FAFC),
        onSurface = Color(0xFF171C20),
        surfaceVariant = Color(0xFFDDE3EA),
        onSurfaceVariant = Color(0xFF41484D),
        outline = Color(0xFF71787E),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8CCBF2),
        onPrimary = Color(0xFF00344F),
        primaryContainer = Color(0xFF004B70),
        onPrimaryContainer = Color(0xFFCCE6FF),
        secondary = Color(0xFF6FD6C6),
        onSecondary = Color(0xFF003731),
        secondaryContainer = Color(0xFF005048),
        onSecondaryContainer = Color(0xFFB2F0E6),
        background = Color(0xFF0E1418),
        onBackground = Color(0xFFDEE3E8),
        surface = Color(0xFF0E1418),
        onSurface = Color(0xFFDEE3E8),
        surfaceVariant = Color(0xFF41484D),
        onSurfaceVariant = Color(0xFFC1C7CE),
        outline = Color(0xFF8B9198),
    )

/** Design-system entry point. Colors, typography and spacing live in this package only. */
@Composable
fun PursuitOfWindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** Spacing tokens. Use these instead of raw dp values in screens. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}
