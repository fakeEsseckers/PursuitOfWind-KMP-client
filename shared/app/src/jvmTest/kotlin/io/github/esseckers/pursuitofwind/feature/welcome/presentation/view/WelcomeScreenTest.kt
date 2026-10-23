package io.github.esseckers.pursuitofwind.feature.welcome.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.checkTexts
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The welcome screen on the desktop runtime. */
@OptIn(ExperimentalTestApi::class)
class WelcomeScreenTest {
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `the app says what it answers and get started moves on`() =
        runComposeUiTest {
            var started = 0
            setContent { PursuitOfWindTheme(darkTheme = false) { WelcomeScreen(onGetStarted = { started++ }) } }

            onNode(isHeading() and hasText("Pursuit Of Wind")).assertIsDisplayed()
            onNode(hasText("Know when to go. Know who’s driving.")).assertIsDisplayed()
            onNodeWithTag("welcome.start").performScrollTo().assert(hasText("Get started")).performClick()

            assertEquals(1, started)
        }

    @Test
    fun `russian speakers are welcomed in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            setContent { PursuitOfWindTheme(darkTheme = false) { WelcomeScreen(onGetStarted = {}) } }

            onNodeWithTag("welcome.start").performScrollTo().assert(hasText("Начать"))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    PursuitOfWindTheme(darkTheme = false) { WelcomeScreen(onGetStarted = {}) }
                }
            }

            val fit = checkTexts()

            assertTrue("Get started" in fit.checked, "The button was never measured: ${fit.checked}")
            assertEquals(emptySet(), fit.cutOff)
        }
}
