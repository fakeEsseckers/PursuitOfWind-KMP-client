package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailIntent
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.preview.SpotDetailPreviewData
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** The spot detail screen on the desktop runtime: what each state shows, what its controls send, and how it reads. */
@OptIn(ExperimentalTestApi::class)
class SpotDetailScreenTest {
    private val intents = mutableListOf<SpotDetailIntent>()
    private var backs = 0
    private val systemLocale = Locale.getDefault()
    private val data = SpotDetailPreviewData

    /** The hour of the chart that is selected. */
    private val selectedHour =
        SemanticsMatcher("the selected hour") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("spot.hour.") == true &&
                node.config.getOrNull(SemanticsProperties.Selected) == true
        }

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    private fun ComposeUiTest.show(
        state: SpotDetailState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(
                    darkTheme = false,
                ) { SpotDetailScreen(state = state, onIntent = { intents += it }, onBack = { backs++ }) }
            }
        }
    }

    @Test
    fun `the header names the spot and says how far it is and how old the forecast is`() =
        runComposeUiTest {
            show(data.ready)

            onNode(isHeading() and hasText("Nordstrand Bay"))
                .assertIsDisplayed()
                .assert(hasText("2 h drive · Updated 25 min ago"))
        }

    @Test
    fun `every hour of the chart reads as text and a tap shows its details`() =
        runComposeUiTest {
            show(data.ready)

            onNodeWithTag("spot.hour.5")
                .assert(hasContentDescription("Today 13:00: 20 kn, gusts 25 kn, wind from the west, GO"))
                .assert(hasClickAction())
                .performClick()
            onNodeWithTag("spot.hour.0").assert(hasContentDescription("Today 08:00:", substring = true))

            assertEquals(listOf<SpotDetailIntent>(SpotDetailIntent.SelectHour(data.saturday8am + 5.hours)), intents)
        }

    @Test
    fun `the selected hour is marked for screen readers and shown in words`() =
        runComposeUiTest {
            show(data.ready)

            onNodeWithTag("spot.hour.4").assertIsSelected()
            onNodeWithTag("spot.hour.5").assertIsNotSelected()
            onNodeWithTag("spot.details")
                .assert(hasText("Today 12:00"))
                .assert(hasText("GO"))
                .assert(hasText("Wind from the southwest", substring = true))
                .assert(hasText("Everything fits this hour."))
        }

    @Test
    fun `the details of a NO-GO hour say why`() =
        runComposeUiTest {
            show(data.state(selection = Selection.Hour(data.saturday8am)))

            onNodeWithTag("spot.details")
                .assert(hasText("NO-GO"))
                .assert(hasText("• Not enough wind"))
                .assert(hasText("• Outside your riding hours"))
                .assert(hasText("Models: ECMWF 9 · GFS 11 · ICON", substring = true))
        }

    @Test
    fun `day chips and windows move the chart`() =
        runComposeUiTest {
            show(data.ready)

            onNodeWithTag("spot.day.2026-10-24").assertIsSelected()
            onNodeWithTag("spot.day.2026-10-25").performClick()
            onNodeWithTag("spot.window.4")
                .performScrollTo()
                .assert(hasText("Today, 12:00–17:00"))
                .assert(hasText("Leave by 09:30"))
                .performClick()

            assertEquals(
                listOf(SpotDetailIntent.SelectDay(LocalDate(2026, 10, 25)), SpotDetailIntent.SelectHour(data.saturday8am + 4.hours)),
                intents,
            )
        }

    @Test
    fun `a spot opened for a later day shows its hour on the first frame`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            show(data.state(selection = Selection.Day(LocalDate(2026, 10, 26))))

            // Monday is two days into the chart, well past the window's width: it shows without a scroll that comes later.
            onNode(selectedHour).assertIsDisplayed()
        }

    @Test
    fun `on a phone the hour's details and the windows sit one under the other`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH_PX, PHONE_HEIGHT_PX), density = Density(PHONE_DENSITY)) {
            show(data.ready)

            val details = onNodeWithTag("spot.details").getUnclippedBoundsInRoot()
            val windows = onNodeWithTag("spot.windows").getUnclippedBoundsInRoot()
            assertEquals(details.left, windows.left)
            assertTrue(windows.top >= details.bottom, "$details $windows")
        }

    @Test
    fun `on a tablet the hour's details and the windows sit side by side`() =
        runSkikoComposeUiTest(size = Size(TABLET_WIDTH, TABLET_HEIGHT)) {
            show(data.ready)

            val details = onNodeWithTag("spot.details").getUnclippedBoundsInRoot()
            val windows = onNodeWithTag("spot.windows").getUnclippedBoundsInRoot()
            assertEquals(details.top, windows.top)
            assertTrue(windows.left >= details.right, "$details $windows")
        }

    @Test
    fun `time runs left to right in right to left languages too`() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PursuitOfWindTheme { SpotDetailScreen(state = data.ready, onIntent = {}, onBack = {}) }
                }
            }

            val first = onNodeWithTag("spot.hour.0").fetchSemanticsNode().boundsInRoot
            val second = onNodeWithTag("spot.hour.1").fetchSemanticsNode().boundsInRoot
            assertTrue(first.left < second.left)
        }

    @Test
    fun `back and refresh are buttons in the top bar`() =
        runComposeUiTest {
            show(data.ready)

            onNodeWithContentDescription("Back").performClick()
            onNodeWithContentDescription("Refresh").performClick()

            assertEquals(1, backs)
            assertEquals(listOf<SpotDetailIntent>(SpotDetailIntent.Refresh), intents)
        }

    @Test
    fun `offline the chart stays with a notice and a retry button`() =
        runComposeUiTest {
            show(data.offline)

            onNodeWithTag("spot.notice").assert(hasText("No connection. Showing the last saved forecast."))
            onNodeWithTag("spot.chart").assertIsDisplayed()
            onNodeWithTag("spot.retry").performClick()

            assertEquals(listOf<SpotDetailIntent>(SpotDetailIntent.Refresh), intents)
        }

    @Test
    fun `loading and the error and a spot not followed each have their own state`() =
        runComposeUiTest {
            var state by mutableStateOf(data.loading)
            setContent { PursuitOfWindTheme { SpotDetailScreen(state = state, onIntent = { intents += it }, onBack = {}) } }

            onNodeWithTag("spot.loading").assert(hasContentDescription("Loading the forecast"))
            state = data.error
            onNodeWithTag("spot.error").assert(hasAnyDescendant(hasText("No connection. Check your internet and try again.")))
            onNodeWithTag("spot.retry").performClick()
            state = data.notFollowed
            onNodeWithTag("spot.missing").assert(hasAnyDescendant(hasText("Not one of your spots")))

            assertEquals(listOf<SpotDetailIntent>(SpotDetailIntent.Refresh), intents)
        }

    @Test
    fun `russian speakers get the screen in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(data.ready)

            onNode(isHeading() and hasText("Nordstrand Bay")).assert(hasText("2 часа в дороге · Обновлено 25 минут назад"))
            onNodeWithTag("spot.hour.5").assert(hasContentDescription("Сегодня 13:00: 20 уз, порывы 25 уз, ветер западный, ПОГНАЛИ"))
            onNodeWithTag("spot.details").assert(hasText("Ветер юго-западный", substring = true))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH_PX, PHONE_HEIGHT_PX), density = Density(PHONE_DENSITY)) {
            show(data.ready, fontScale = 2f)

            val screenWidth = onRoot().fetchSemanticsNode().size.width
            val checked = mutableSetOf<String>()
            val cutOff = mutableSetOf<String>()
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .forEach { node ->
                    val text =
                        node.config
                            .getOrNull(SemanticsProperties.Text)
                            ?.joinToString()
                            .orEmpty()
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    checked += text
                    if (layouts.any { it.didOverflowHeight } || node.boundsInRoot.left < 0f || node.boundsInRoot.right > screenWidth) {
                        cutOff += text
                    }
                }

            assertTrue(checked.any { "is not safety advice" in it }, "The end of the screen was never measured: $checked")
            assertEquals(emptySet(), cutOff)
        }

    private companion object {
        // A 411 × 891 dp phone at 420 dpi.
        const val PHONE_WIDTH_PX = 1080f
        const val PHONE_HEIGHT_PX = 2340f
        const val PHONE_DENSITY = 2.625f

        /** A tablet in landscape at a density of 1. */
        const val TABLET_WIDTH = 1280f
        const val TABLET_HEIGHT = 800f
    }
}
