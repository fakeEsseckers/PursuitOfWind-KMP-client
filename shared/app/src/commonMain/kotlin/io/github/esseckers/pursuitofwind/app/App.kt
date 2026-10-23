package io.github.esseckers.pursuitofwind.app

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.app.navigation.AppNavigation
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme

/**
 * Root composable shared by the Android and iOS apps. Koin must be started first (`startPursuitOfWind` on Android,
 * `MainViewController` on iOS).
 */
@Composable
fun App() {
    PursuitOfWindTheme {
        AppNavigation()
    }
}
