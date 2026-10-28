package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapIntent
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.beach
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.saltLagoon
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview.SpotMapPreviewData.westOfBeach
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.ScriptedSpotMap
import io.github.esseckers.pursuitofwind.testing.checkTexts
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The map of spots on the desktop runtime, with a scripted map: what each panel shows and what the map and buttons send. */
@OptIn(ExperimentalTestApi::class)
class SpotMapScreenTest {
    private val intents = mutableListOf<SpotMapIntent>()
    private var backs = 0
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
        state: SpotMapState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(darkTheme = false) {
                    SpotMapScreen(state = state, map = map, onIntent = { intents += it }, onBack = { backs++ })
                }
            }
        }
    }

    @Test
    fun `the map waits for the spots`() =
        runComposeUiTest {
            show(SpotMapPreviewData.loading)

            onNode(hasTestTag("spotmap.loading") and hasContentDescription("Loading the spots…")).assertExists()
            onNodeWithTag("map").assertDoesNotExist()
            assertNull(map.shown)
        }

    @Test
    fun `the map gets the spots and says how to add one`() =
        runComposeUiTest {
            show(SpotMapPreviewData.browsing)

            onNode(isHeading() and hasText("Spots on the map")).assertIsDisplayed()
            val hint = "Long-press the beach where you launch to add a spot, or tap a spot to add it to yours."
            onNodeWithTag("spotmap.hint").assert(hasAnyDescendant(hasText(hint)))
            assertEquals(SpotMapPreviewData.browsing.map, map.shown)
        }

    @Test
    fun `presses, taps and spots on the map become intents`() =
        runComposeUiTest {
            show(SpotMapPreviewData.browsing)

            runOnIdle {
                map.longPress(beach)
                map.tap(westOfBeach)
                map.tapSpot(saltLagoon.id)
            }

            assertEquals(
                listOf(SpotMapIntent.LongPress(beach), SpotMapIntent.Tap(westOfBeach), SpotMapIntent.SelectSpot(saltLagoon.id)),
                intents,
            )
        }

    @Test
    fun `screen readers hear the map and pick its spots from it`() =
        runComposeUiTest {
            show(SpotMapPreviewData.browsing)

            val actions =
                onNodeWithTag(
                    "map",
                ).assert(hasContentDescription("Map of spots")).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertEquals(listOf("Select Dune Point", "Select Nordstrand Bay", "Select Salt Lagoon"), actions.map { it.label })
            runOnIdle { actions.last().action() }

            assertEquals(listOf<SpotMapIntent>(SpotMapIntent.SelectSpot(saltLagoon.id)), intents)
        }

    @Test
    fun `the map keeps its credits clear of the panel`() =
        runComposeUiTest {
            show(SpotMapPreviewData.placed)

            val panel = onNodeWithTag("spotmap.bottom").fetchSemanticsNode().size.height
            val covered = runOnIdle { map.covered?.calculateBottomPadding() }
            assertEquals(with(density) { panel.toDp() }, covered)
        }

    @Test
    fun `a pin asks for the water and cannot be saved yet`() =
        runComposeUiTest {
            show(SpotMapPreviewData.pinned)

            onNodeWithTag(
                "spotmap.water",
            ).assert(
                hasAnyDescendant(
                    hasText("Now tap the water next to the pin, where you go in. That tells the app which winds are safe to ride here."),
                ),
            )
            onNodeWithTag("spotmap.save").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("spotmap.cancel").performScrollTo().performClick()

            assertEquals(listOf<SpotMapIntent>(SpotMapIntent.Cancel), intents)
        }

    @Test
    fun `a placed spot says where its water is and saves with its drive`() =
        runComposeUiTest {
            show(SpotMapPreviewData.placed)

            onNodeWithTag("spotmap.water").assert(hasAnyDescendant(hasText("Water to the west")))
            onNodeWithTag("spotmap.legend").assert(hasAnyDescendant(hasText("Wind from the green side: you can ride")))
            onNodeWithTag("spotmap.drive").performScrollTo().assert(hasAnyDescendant(hasText("1 h")))
            onNodeWithContentDescription("Longer drive to Sand Spit").performClick()
            onNodeWithTag("spotmap.save").performScrollTo().assertIsEnabled().performClick()

            assertEquals(listOf(SpotMapIntent.ChangeDrive(Step.UP), SpotMapIntent.Save), intents)
        }

    @Test
    fun `a failed save says why`() =
        runComposeUiTest {
            show(SpotMapPreviewData.saveFailed)

            onNodeWithTag("spotmap.error").performScrollTo().assert(hasText("No connection. Check your internet and try again."))
        }

    @Test
    fun `a spot near the pin is offered first`() =
        runComposeUiTest {
            show(SpotMapPreviewData.nearby)

            onNodeWithTag("spotmap.nearby").assert(hasText("Nordstrand Bay is 300 m from here. Is it the same spot?"))
            onNodeWithTag("spotmap.useNearby").assert(hasText("Yes, it’s Nordstrand Bay")).performClick()
            onNodeWithTag("spotmap.newHere").performClick()

            assertEquals(listOf(SpotMapIntent.UseNearby, SpotMapIntent.AddHere), intents)
        }

    @Test
    fun `someone else's spot can be followed but not changed`() =
        runComposeUiTest {
            show(SpotMapPreviewData.selected)

            onNode(isHeading() and hasText("Salt Lagoon")).assertIsDisplayed()
            onNodeWithTag("spotmap.edit").assertDoesNotExist()
            onNodeWithTag("spotmap.follow").performScrollTo().assert(hasText("Add to my spots")).performClick()

            assertEquals(listOf<SpotMapIntent>(SpotMapIntent.Follow), intents)
        }

    @Test
    fun `the rider's own spot, already followed, can be changed`() =
        runComposeUiTest {
            show(SpotMapPreviewData.selectedMine)

            onNodeWithTag("spotmap.panel").assert(hasAnyDescendant(hasText("In your spots")))
            onNodeWithTag("spotmap.follow").assertDoesNotExist()
            onNodeWithTag("spotmap.edit").performClick()

            assertEquals(listOf<SpotMapIntent>(SpotMapIntent.EditSelected), intents)
        }

    @Test
    fun `a spot being changed has no drive and no cancel`() =
        runComposeUiTest {
            show(SpotMapPreviewData.editing)

            onNode(isHeading() and hasText("Change the spot")).assertIsDisplayed()
            onNodeWithTag("spotmap.water").assert(hasAnyDescendant(hasText("Water to the northwest")))
            onNodeWithTag("spotmap.drive").assertDoesNotExist()
            onNodeWithTag("spotmap.cancel").assertDoesNotExist()
        }

    @Test
    fun `a map that did not load says so and loads again`() =
        runComposeUiTest {
            show(SpotMapPreviewData.offline)
            runOnIdle { map.baseMap(loaded = false) }

            onNodeWithTag("spotmap.basemap.notice").assert(hasText("The map did not load. Check your connection."))
            onNodeWithTag("spotmap.basemap.retry").performClick()

            assertEquals(listOf(SpotMapIntent.BaseMap(loaded = false), SpotMapIntent.RetryBaseMap), intents)
        }

    @Test
    fun `old spots say so and refresh again`() =
        runComposeUiTest {
            show(SpotMapPreviewData.offline)

            onNodeWithTag("spotmap.retry").performClick()
            onNodeWithTag("spotmap.refresh").performClick()

            assertEquals(listOf<SpotMapIntent>(SpotMapIntent.Refresh, SpotMapIntent.Refresh), intents)
        }

    @Test
    fun `russian speakers get the map in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(SpotMapPreviewData.placed)

            onNode(isHeading() and hasText("Споты на карте")).assertIsDisplayed()
            onNodeWithTag("spotmap.water").assert(hasAnyDescendant(hasText("Вода на западе")))
        }

    @Test
    fun `the panel leaves room for the map and no text is cut off at twice the font size`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(SpotMapPreviewData.saveFailed, fontScale = 2f)

            val panel = onNodeWithTag("spotmap.bottom").fetchSemanticsNode().boundsInRoot
            val screen = onNodeWithTag("map").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.height <= screen.height * 0.6f + 1f, "panel $panel on map $screen")
            val checked = mutableSetOf<String>()
            val cutOff = mutableSetOf<String>()
            // The panel scrolls, so bring each part in before measuring its text.
            listOf("spotmap.water", "spotmap.name", "spotmap.drive", "spotmap.error", "spotmap.cancel").forEach { tag ->
                onNodeWithTag(tag).performScrollTo()
                val fit = checkTexts()
                checked += fit.checked
                cutOff += fit.cutOff
            }

            assertTrue("Cancel" in checked, "The end of the panel was never measured: $checked")
            assertEquals(emptySet(), cutOff)
        }
}
