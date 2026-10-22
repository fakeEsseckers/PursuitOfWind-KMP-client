package io.github.esseckers.pursuitofwind.ui

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.ui.design.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.ui.today.TodayRoute

/**
 * Root composable shared by the Android and iOS apps. Koin must be started first (`startPursuitOfWind` on Android,
 * `MainViewController` on iOS). Navigation arrives with the second screen (Issue #8).
 */
@Composable
fun App() {
    PursuitOfWindTheme {
        TodayRoute()
    }
}
