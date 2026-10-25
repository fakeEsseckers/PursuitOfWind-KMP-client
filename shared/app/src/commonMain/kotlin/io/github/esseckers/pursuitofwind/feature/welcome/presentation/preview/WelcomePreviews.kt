package io.github.esseckers.pursuitofwind.feature.welcome.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.welcome.presentation.view.WelcomeScreen

@Preview
@Composable
private fun WelcomePreview() = PursuitOfWindTheme(darkTheme = false) { WelcomeScreen(onGetStarted = {}) }

@Preview
@Composable
private fun WelcomeDarkPreview() = PursuitOfWindTheme(darkTheme = true) { WelcomeScreen(onGetStarted = {}) }
