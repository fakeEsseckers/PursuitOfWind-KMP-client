package io.github.esseckers.pursuitofwind.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What a screen's `Scaffold` keeps its content clear of: the system bars and display cutouts above and at the sides.
 * The bottom is left to the content, which scrolls behind the navigation bar and ends clear of it with
 * [contentEndInsets]; the keyboard is left to the forms, which use `imePadding()`.
 */
@Composable
internal fun screenInsets(): WindowInsets =
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/**
 * Room at the end of a screen's scrolling content for what covers the bottom of the window: the navigation bar, a
 * cutout or the keyboard. Nothing above a tab bar, which takes that room itself; nothing below a keyboard a form has
 * already made room for.
 */
@Composable
internal fun contentEndInsets(): WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

/**
 * Keeps three-button navigation readable over content that scrolls behind it, as the system's own scrim would: a strip
 * as high as the buttons, in the screen's background. Gesture navigation has no buttons, so nothing shows there, nor
 * above a tab bar, which takes the bottom itself. Goes last in a `Box`, at the bottom.
 */
@Composable
internal fun NavigationBarScrim(modifier: Modifier = Modifier) {
    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.tappableElement)
                .background(MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA)),
    )
}

/** As opaque as the system's light scrim behind three-button navigation. */
private const val SCRIM_ALPHA = 0.9f
