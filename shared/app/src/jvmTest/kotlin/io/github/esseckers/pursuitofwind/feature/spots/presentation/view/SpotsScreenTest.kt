package io.github.esseckers.pursuitofwind.feature.spots.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsIntent
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.preview.SpotsPreviewData
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.checkTexts
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Spots tab on the desktop runtime: what each state shows, what its controls send, and how it reads. */
@OptIn(ExperimentalTestApi::class)
class SpotsScreenTest {
    private val intents = mutableListOf<SpotsIntent>()
    private val systemLocale = Locale.getDefault()
    private val saltLagoon = SpotId("salt-lagoon")

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    private fun ComposeUiTest.show(
        state: SpotsState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(darkTheme = false) { SpotsScreen(state = state, onIntent = { intents += it }) }
            }
        }
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag("myspots.list").performScrollToNode(hasTestTag(tag))
    }

    private fun switch(on: Boolean) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, if (on) ToggleableState.On else ToggleableState.Off)

    @Test
    fun `each spot reads as a heading with its favorite switch and drive`() =
        runComposeUiTest {
            show(SpotsPreviewData.ready)

            onNode(isHeading() and hasText("Spots")).assertIsDisplayed()
            scrollTo("myspots.card.nordstrand-bay")
            onNode(isHeading() and hasText("Nordstrand Bay")).assertIsDisplayed()
            onNodeWithTag("myspots.favorite.nordstrand-bay").assert(switch(on = true))
            onNodeWithTag("myspots.drive.nordstrand-bay").assert(hasAnyDescendant(hasText("2 h")))
            scrollTo("myspots.card.salt-lagoon")
            onNodeWithTag("myspots.favorite.salt-lagoon").assert(switch(on = false))
            scrollTo("myspots.card.lake-weiss")
            onNodeWithTag("myspots.card.lake-weiss").assert(hasAnyDescendant(hasText("Wind sectors not checked yet")))
        }

    @Test
    fun `the controls of a card send their intents for that spot`() =
        runComposeUiTest {
            show(SpotsPreviewData.ready)

            scrollTo("myspots.card.salt-lagoon")
            onNodeWithTag("myspots.favorite.salt-lagoon").performClick()
            onNodeWithContentDescription("Longer drive to Salt Lagoon").performClick()
            onNodeWithTag("myspots.open.salt-lagoon").performClick()
            onNodeWithContentDescription("Remove Salt Lagoon").performClick()
            onNodeWithTag("myspots.add").performClick()

            assertEquals(
                listOf(
                    SpotsIntent.ToggleFavorite(saltLagoon),
                    SpotsIntent.ChangeDrive(saltLagoon, 175),
                    SpotsIntent.OpenSpot(saltLagoon),
                    SpotsIntent.Remove(saltLagoon),
                    SpotsIntent.AddSpots,
                ),
                intents,
            )
        }

    @Test
    fun `a change on its way says so`() =
        runComposeUiTest {
            show(SpotsPreviewData.saving)

            scrollTo("myspots.card.salt-lagoon")
            onNodeWithTag("myspots.saving.salt-lagoon").assert(hasText("Saving…"))
            onNodeWithTag("myspots.favorite.salt-lagoon").assert(switch(on = true))
        }

    @Test
    fun `with no spots the message offers to add some`() =
        runComposeUiTest {
            show(SpotsPreviewData.empty)

            onNodeWithTag("myspots.empty").assert(hasAnyDescendant(hasText("No spots yet")))
            onNodeWithTag("myspots.add").assertDoesNotExist()
            onNodeWithTag("myspots.addFirst").performClick()

            assertEquals(listOf<SpotsIntent>(SpotsIntent.AddSpots), intents)
        }

    @Test
    fun `offline the saved spots stay with a notice and errors offer a retry`() =
        runComposeUiTest {
            show(SpotsPreviewData.offline)

            onNodeWithTag("myspots.notice").assert(hasText("No connection. Showing the saved list of spots."))
            onNodeWithTag("myspots.retry").performClick()

            assertEquals(listOf<SpotsIntent>(SpotsIntent.Refresh), intents)
        }

    @Test
    fun `nothing cached and no connection offers a retry`() =
        runComposeUiTest {
            show(SpotsPreviewData.error)

            onNodeWithTag("myspots.error").assert(hasAnyDescendant(hasText("Couldn’t load your spots")))
            onNodeWithTag("myspots.retry").performClick()

            assertEquals(listOf<SpotsIntent>(SpotsIntent.Refresh), intents)
        }

    @Test
    fun `loading is announced and screen readers can refresh from the title`() =
        runComposeUiTest {
            show(SpotsPreviewData.loading)

            onNode(hasTestTag("myspots.loading") and hasContentDescription("Loading your spots")).assertExists()
            val refresh =
                onNode(isHeading() and hasText("Spots"))
                    .fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
                    .single { it.label == "Refresh" }
            runOnIdle { refresh.action() }

            assertEquals(listOf<SpotsIntent>(SpotsIntent.Refresh), intents)
        }

    @Test
    fun `russian speakers get the tab in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(SpotsPreviewData.ready)

            onNode(isHeading() and hasText("Споты")).assertIsDisplayed()
            scrollTo("myspots.card.nordstrand-bay")
            onNodeWithTag("myspots.favorite.nordstrand-bay").assert(hasText("Избранный"))
            onNodeWithContentDescription("Убрать Nordstrand Bay").assertExists()
            onNodeWithTag("myspots.add").assert(hasText("Добавить споты"))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(SpotsPreviewData.ready, fontScale = 2f)

            val checked = mutableSetOf<String>()
            val cutOff = mutableSetOf<String>()
            // The list only lays out what is on screen, so bring each card in before measuring its text.
            listOf("lake-weiss", "nordstrand-bay", "salt-lagoon").forEach { id ->
                scrollTo("myspots.card.$id")
                val fit = checkTexts()
                checked += fit.checked
                cutOff += fit.cutOff
            }

            assertTrue("Salt Lagoon" in checked, "The last card was never measured: $checked")
            assertEquals(emptySet(), cutOff)
        }
}
