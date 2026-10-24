package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferIntent
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.preview.RideOfferPreviewData
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.checkTexts
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ride offer form on the desktop runtime: what it shows and what its controls send. */
@OptIn(ExperimentalTestApi::class)
class RideOfferScreenTest {
    private val intents = mutableListOf<RideOfferIntent>()
    private var backs = 0
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    private fun ComposeUiTest.show(
        state: RideOfferState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(darkTheme = false) { RideOfferScreen(state = state, onIntent = { intents += it }, onBack = { backs++ }) }
            }
        }
    }

    @Test
    fun `the form says which window the ride is for and sends what the rider sets`() =
        runComposeUiTest {
            show(RideOfferPreviewData.empty)

            onNode(isHeading() and hasText("Offer a ride")).assertIsDisplayed()
            onNodeWithTag("offer.summary").assert(hasAnyDescendant(isHeading() and hasText("Nordstrand Bay")))
            onNodeWithTag("offer.summary").assert(hasAnyDescendant(hasText("Saturday, window 13:00–17:00")))
            onNodeWithTag("offer.summary").assert(hasAnyDescendant(hasText("You leave at 10:30")))
            onNodeWithTag("offer.submit").assertIsNotEnabled()
            onNodeWithTag("offer.crew.crw_owls").performClick()
            onNodeWithContentDescription("More free seats").performClick()
            onNodeWithContentDescription("Fewer free seats").performClick()
            onNodeWithTag("offer.meeting").performTextInput("Old harbour")
            onNodeWithContentDescription("Back").performClick()

            assertEquals(
                listOf(
                    RideOfferIntent.ChooseCrew("crw_owls"),
                    RideOfferIntent.MoreSeats,
                    RideOfferIntent.FewerSeats,
                    RideOfferIntent.ChangeMeetingPoint("Old harbour"),
                ),
                intents,
            )
            assertEquals(1, backs)
        }

    @Test
    fun `a filled form offers the ride and a failed one says why`() =
        runComposeUiTest {
            show(RideOfferPreviewData.failed)

            onNodeWithTag("offer.error").assert(hasText("Couldn’t offer the ride. No connection. Check your internet and try again."))
            onNodeWithTag("offer.submit").assertIsEnabled().performClick()

            assertEquals(listOf<RideOfferIntent>(RideOfferIntent.Submit), intents)
        }

    @Test
    fun `while the offer goes out the button takes no taps`() =
        runComposeUiTest {
            show(RideOfferPreviewData.sending)

            onNodeWithTag("offer.submit").assertIsNotEnabled()
        }

    @Test
    fun `without a crew the form says where to get one`() =
        runComposeUiTest {
            show(RideOfferPreviewData.noCrew)

            onNodeWithTag("offer.noCrew").assert(hasAnyDescendant(hasText("No crew yet")))
            onNodeWithTag("offer.submit").assertDoesNotExist()
        }

    @Test
    fun `russian speakers get the form in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(RideOfferPreviewData.filled)

            onNode(isHeading() and hasText("Предложить поездку")).assertIsDisplayed()
            onNodeWithTag("offer.summary").assert(hasAnyDescendant(hasText("Выезжаешь в 10:30")))
            onNodeWithTag("offer.submit").assert(hasText("Предложить поездку"))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(RideOfferPreviewData.failed, fontScale = 2f)

            val fit = checkTexts()

            assertTrue("Nordstrand Bay" in fit.checked, fit.checked.toString())
            assertEquals(emptySet(), fit.cutOff)
        }
}
