package io.github.esseckers.pursuitofwind.app

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.today.presentation.view.TodayRoute

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
