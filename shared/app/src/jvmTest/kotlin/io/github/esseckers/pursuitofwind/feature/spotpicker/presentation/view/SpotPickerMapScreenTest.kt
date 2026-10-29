package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickerView
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview.SpotPickerPreviewData
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview.SpotPickerPreviewData.dune
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview.SpotPickerPreviewData.lakeWeiss
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

/** The spot picker's map on the desktop runtime, with a scripted map: what it shows and what it sends. */
@OptIn(ExperimentalTestApi::class)
class SpotPickerMapScreenTest {
    private val intents = mutableListOf<SpotPickerIntent>()
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
                PursuitOfWindTheme(darkTheme = false) {
                    SpotPickerScreen(state = state, map = map, onIntent = { intents += it }, onBack = {}, onOpenMap = { maps++ })
                }
            }
        }
    }

    @Test
    fun `the switch shows the catalog as a list or on the map`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.chosen)

            onNodeWithTag("spots.view.LIST").assert(hasText("List")).assert(isSelected())
            onNodeWithTag("spots.view.MAP").assert(hasText("Map")).performClick()
            onNodeWithTag("map").assertDoesNotExist()

            assertEquals(listOf<SpotPickerIntent>(SpotPickerIntent.Show(PickerView.MAP)), intents)
        }

    @Test
    fun `the map gets the catalog and a spot picked on it shows under it to choose and copy`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.onMap)

            assertEquals(SpotPickerPreviewData.onMap.map, map.shown)
            onNodeWithTag("spots.toggle.lake-weiss").assert(hasText("Lake Weiss")).performClick()
            onNodeWithTag("spots.coordinates.lake-weiss").assert(hasAnyDescendant(hasText("52.41230, 11.62740")))
            onNodeWithTag("spots.copy.lake-weiss").performClick()
            onNodeWithTag("spots.view.LIST").performClick()

            assertEquals(
                listOf(
                    SpotPickerIntent.Toggle(lakeWeiss.id),
                    SpotPickerIntent.CopyCoordinates(lakeWeiss.id),
                    SpotPickerIntent.Show(PickerView.LIST),
                ),
                intents,
            )
        }

    @Test
    fun `taps on the spots pick them and a tap beside them puts the pick away`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.onMap)

            runOnIdle {
                map.tapSpot(dune.id)
                map.tap(GeoPoint(54.0, 9.0))
                map.longPress(GeoPoint(54.0, 9.0))
            }

            assertEquals(listOf(SpotPickerIntent.PickOnMap(dune.id), SpotPickerIntent.TapMap), intents)
        }

    @Test
    fun `before a spot is picked the map says how and offers to add one`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.mapOffline)

            onNodeWithTag("spots.mapHint").assert(hasAnyDescendant(hasText("Tap a spot to choose it.")))
            onNodeWithTag("spots.mapAdd").assert(hasText("Add a spot")).performClick()

            assertEquals(1, maps)
        }

    @Test
    fun `a map that did not load says so and loads again`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.mapOffline)
            runOnIdle { map.baseMap(loaded = false) }

            onNodeWithTag("spots.basemap.notice").assert(hasText("The map did not load. Check your connection."))
            onNodeWithTag("spots.basemap.retry").performClick()

            assertEquals(listOf(SpotPickerIntent.BaseMap(loaded = false), SpotPickerIntent.RetryBaseMap), intents)
        }

    @Test
    fun `offline the map says the spots are the saved ones and refreshes them`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.mapOffline)

            onNodeWithTag("spots.notice").assert(hasText("No connection. Showing the saved list of spots."))
            onNodeWithTag("spots.retry").performClick()

            assertEquals(listOf<SpotPickerIntent>(SpotPickerIntent.Refresh), intents)
        }

    @Test
    fun `screen readers hear the map and pick its spots from it`() =
        runComposeUiTest {
            show(SpotPickerPreviewData.onMap)

            val actions =
                onNodeWithTag("map")
                    .assert(hasContentDescription("Map of spots"))
                    .fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions]
            assertEquals(
                listOf("Select Dune Point", "Select Lake Weiss", "Select Nordstrand Bay", "Select Salt Lagoon"),
                actions.map { it.label }.sorted(),
            )
            runOnIdle { actions.first { it.label == "Select Dune Point" }.action() }

            assertEquals(listOf<SpotPickerIntent>(SpotPickerIntent.PickOnMap(dune.id)), intents)
        }

    @Test
    fun `russian speakers get the switch and the hint in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(SpotPickerPreviewData.mapOffline)

            onNodeWithTag("spots.view.LIST").assert(hasText("Список"))
            onNodeWithTag("spots.view.MAP").assert(hasText("Карта"))
            onNodeWithTag("spots.mapHint").assert(hasAnyDescendant(hasText("Нажми на спот, чтобы выбрать его.")))
        }

    @Test
    fun `the panel leaves room for the map and no text is cut off at twice the font size`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(SpotPickerPreviewData.onMap, fontScale = 2f)

            val panel = onNodeWithTag("spots.mapPanel").fetchSemanticsNode().boundsInRoot
            val shown = onNodeWithTag("map").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.height <= shown.height * 0.6f + 1f, "panel $panel on map $shown")
            val fit = checkTexts()

            assertTrue("Lake Weiss" in fit.checked, "The picked spot was never measured: ${fit.checked}")
            assertEquals(emptySet(), fit.cutOff)
        }
}
