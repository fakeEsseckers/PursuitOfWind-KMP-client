package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview.SpotPickerPreviewData
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.ScriptedSpotMap
import io.github.esseckers.pursuitofwind.testing.checkTexts
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The spot picker on the desktop runtime: what each state shows, what its rows and buttons send, and how it reads. */
@OptIn(ExperimentalTestApi::class)
class SpotPickerScreenTest {
    private val intents = mutableListOf<SpotPickerIntent>()
    private var backs = 0
    private var maps = 0
    private val map = ScriptedSpotMap()
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
        state: SpotPickerState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(
                    darkTheme = false,
                ) { SpotPickerScreen(state = state, map = map, onIntent = { intents += it }, onBack = { backs++ }, onOpenMap = { maps++ }) }
            }
        }
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag("spots.list").performScrollToNode(hasTestTag(tag))
    }

    private fun checked(state: ToggleableState) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    @Test
    fun `each spot is a checkbox with its name and a chosen one shows its drive`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.chosen)

            onNode(isHeading() and hasText("Your spots")).assertIsDisplayed()
            scrollTo("spots.toggle.nordstrand-bay")
            onNodeWithTag("spots.toggle.nordstrand-bay").assert(hasText("Nordstrand Bay")).assert(checked(ToggleableState.On))
            onNodeWithTag("spots.drive.nordstrand-bay").assert(hasAnyDescendant(hasText("2 h")))
            onNodeWithTag("spots.toggle.dune-point").assert(checked(ToggleableState.Off))
            onNodeWithTag("spots.toggle.lake-weiss").assert(hasText("Lake Weiss"))
            onNodeWithTag("spots.done").assert(hasText("Done"))
        }

    @Test
    fun `a row chooses its spot and the drive buttons step it`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.chosen)

            scrollTo("spots.toggle.salt-lagoon")
            onNodeWithTag("spots.toggle.salt-lagoon").performClick()
            scrollTo("spots.drive.nordstrand-bay")
            onNodeWithContentDescription("Longer drive to Nordstrand Bay").performClick()
            onNodeWithContentDescription("Shorter drive to Nordstrand Bay").performClick()
            onNodeWithTag("spots.done").performClick()
            onNodeWithContentDescription("Back").performClick()

            assertEquals(
                listOf(
                    SpotPickerIntent.Toggle(SpotId("salt-lagoon")),
                    SpotPickerIntent.ChangeDrive(SpotId("nordstrand-bay"), Step.UP),
                    SpotPickerIntent.ChangeDrive(SpotId("nordstrand-bay"), Step.DOWN),
                    SpotPickerIntent.Done,
                ),
                intents,
            )
            assertEquals(1, backs)
        }

    @Test
    fun `each spot shows where it is and copies that`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.chosen)

            scrollTo("spots.copy.nordstrand-bay")
            onNodeWithTag("spots.coordinates.nordstrand-bay").assert(hasAnyDescendant(hasText("54.49360, 8.81310")))
            onNodeWithTag(
                "spots.copy.nordstrand-bay",
            ).assert(hasContentDescription("Copy the coordinates of Nordstrand Bay")).performClick()

            assertEquals(listOf<SpotPickerIntent>(SpotPickerIntent.CopyCoordinates(SpotId("nordstrand-bay"))), intents)
        }

    @Test
    fun `with nothing chosen the button skips this step`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.fresh)

            onNodeWithTag("spots.done").assert(hasText("Skip for now"))
        }

    @Test
    fun `offline the saved list stays with a notice and both retries refresh`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.offline)

            onNodeWithTag("spots.notice").assert(hasText("No connection. Showing the saved list of spots."))
            onNodeWithTag("spots.retry").performClick()
            onNodeWithContentDescription("Refresh").performClick()

            assertEquals(listOf<SpotPickerIntent>(SpotPickerIntent.Refresh, SpotPickerIntent.Refresh), intents)
        }

    @Test
    fun `an error without a saved list offers a retry`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.error)

            onNodeWithTag("spots.error").assert(hasAnyDescendant(hasText("Couldn’t load the spots")))
            onNodeWithTag("spots.retry").performClick()

            assertEquals(listOf<SpotPickerIntent>(SpotPickerIntent.Refresh), intents)
        }

    @Test
    fun `an empty catalog says so`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.empty)

            onNodeWithTag("spots.empty").assert(hasAnyDescendant(hasText("No spots yet")))
            onNodeWithTag("spots.done").assert(hasText("Skip for now"))
            onNodeWithTag("spots.emptyMap").assert(hasText("Add a spot")).performClick()

            assertEquals(1, maps)
        }

    @Test
    fun `a spot that is not listed is added on the map`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.chosen)

            onNodeWithTag("spots.map").assert(hasText("Add a spot")).performClick()

            assertEquals(1, maps)
            assertEquals(emptyList(), intents)
        }

    @Test
    fun `a failed save says why above the button`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.saveFailed)

            onNodeWithTag("spots.saveError").assert(hasText("The server is having trouble. Try again in a few minutes."))
        }

    @Test
    fun `loading is announced to screen readers`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.loading)

            onNode(hasTestTag("spots.loading") and hasContentDescription("Loading the spots")).assertExists()
        }

    @Test
    fun `russian speakers get the picker in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(SpotPickerPreviewData.chosen)

            onNode(isHeading() and hasText("Твои споты")).assertIsDisplayed()
            scrollTo("spots.drive.nordstrand-bay")
            onNodeWithTag("spots.drive.nordstrand-bay").assert(hasAnyDescendant(hasText("2 ч")))
            onNodeWithTag("spots.copy.nordstrand-bay").assert(hasContentDescription("Скопировать координаты: Nordstrand Bay"))
            onNodeWithTag("spots.done").assert(hasText("Готово"))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(SpotPickerPreviewData.chosen, fontScale = 2f)

            val checked = mutableSetOf<String>()
            val cutOff = mutableSetOf<String>()
            // The list only lays out what is on screen, so bring each row in before measuring its text.
            listOf("dune-point", "lake-weiss", "nordstrand-bay", "salt-lagoon").forEach { id ->
                scrollTo("spots.spot.$id")
                val fit = checkTexts()
                checked += fit.checked
                cutOff += fit.cutOff
            }

            assertTrue("Salt Lagoon" in checked, "The last row was never measured: $checked")
            assertEquals(emptySet(), cutOff)
        }
}
