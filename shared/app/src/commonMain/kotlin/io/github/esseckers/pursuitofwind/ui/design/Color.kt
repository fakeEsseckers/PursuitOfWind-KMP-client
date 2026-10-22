package io.github.esseckers.pursuitofwind.ui.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Ocean blue primary, sea green secondary. Container and variant roles are set explicitly so nothing falls back to
// Material's baseline palette.
internal val LightColors =
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
        surfaceContainerLow = Color(0xFFF0F4F8),
        surfaceContainer = Color(0xFFEAEFF3),
        surfaceContainerHigh = Color(0xFFE3E9EE),
        outline = Color(0xFF71787E),
        outlineVariant = Color(0xFFC1C7CE),
        error = Color(0xFFB42318),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFDE4E1),
        onErrorContainer = Color(0xFF4A0A05),
    )

internal val DarkColors =
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
        surfaceContainerLow = Color(0xFF161C21),
        surfaceContainer = Color(0xFF1A2025),
        surfaceContainerHigh = Color(0xFF252B30),
        outline = Color(0xFF8B9198),
        outlineVariant = Color(0xFF41484D),
        error = Color(0xFFFF9C90),
        onError = Color(0xFF4A1410),
        errorContainer = Color(0xFF4A1410),
        onErrorContainer = Color(0xFFFFDAD5),
    )

/**
 * One verdict's colors: [accent] for badges and icons (with [onAccent] on top of it), [container] for cards (with
 * [onContainer] on top of it). Every pair has a contrast of at least 4.5:1 in both themes.
 */
@Immutable
internal data class VerdictPalette(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/** Colors with a meaning of their own, which Material's color roles do not cover. */
@Immutable
internal data class SemanticColors(
    val go: VerdictPalette,
    val marginal: VerdictPalette,
    val noGo: VerdictPalette,
    val confidenceHigh: Color,
    val confidenceMedium: Color,
    val confidenceLow: Color,
    val confidenceUnknown: Color,
    val skeleton: Color,
)

internal val LightSemanticColors =
    SemanticColors(
        go = VerdictPalette(Color(0xFF1E7B34), Color(0xFFFFFFFF), Color(0xFFDDF4E2), Color(0xFF0B3D18)),
        marginal = VerdictPalette(Color(0xFF8A5300), Color(0xFFFFFFFF), Color(0xFFFFF0D2), Color(0xFF3D2700)),
        noGo = VerdictPalette(Color(0xFFB42318), Color(0xFFFFFFFF), Color(0xFFFDE4E1), Color(0xFF4A0A05)),
        confidenceHigh = Color(0xFF1E7B34),
        confidenceMedium = Color(0xFF8A5300),
        confidenceLow = Color(0xFFB42318),
        confidenceUnknown = Color(0xFF41484D),
        skeleton = Color(0xFFDDE3EA),
    )

internal val DarkSemanticColors =
    SemanticColors(
        go = VerdictPalette(Color(0xFF7BD88F), Color(0xFF00390F), Color(0xFF123D1D), Color(0xFFC9F2D2)),
        marginal = VerdictPalette(Color(0xFFF2C066), Color(0xFF3F2E05), Color(0xFF3F2E05), Color(0xFFFCE7BD)),
        noGo = VerdictPalette(Color(0xFFFF9C90), Color(0xFF4A1410), Color(0xFF4A1410), Color(0xFFFFDAD5)),
        confidenceHigh = Color(0xFF7BD88F),
        confidenceMedium = Color(0xFFF2C066),
        confidenceLow = Color(0xFFFF9C90),
        confidenceUnknown = Color(0xFFC1C7CE),
        skeleton = Color(0xFF252B30),
    )

internal val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
