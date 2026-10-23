package io.github.esseckers.pursuitofwind.feature.today.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.today.at
import io.github.esseckers.pursuitofwind.feature.today.day
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.ride
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Today screen on the desktop runtime: what each state shows, what its buttons send, and how it reads. */
@OptIn(ExperimentalTestApi::class)
class TodayScreenTest {
    private val intents = mutableListOf<TodayIntent>()
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
        state: TodayState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(darkTheme = false) { TodayScreen(state = state, onIntent = { intents += it }) }
            }
        }
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag("today.days").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `a GO day reads as one card with the spot and window and kite and departure and the ride`() =
        runComposeUiTest {
            show(TodayPreviewData.ready)
            scrollTo("today.day.2026-10-24")

            onNodeWithTag("today.day.2026-10-24")
                .assertIsDisplayed()
                .assert(hasText("GO"))
                .assert(hasText("Saturday"))
                .assert(hasText("Nordstrand Bay"))
                .assert(hasText("13:00–17:00"))
                .assert(hasText("18–22 kn"))
                .assert(hasText("Kite 9 m²"))
                .assert(hasText("Leave by 10:30"))
                .assert(hasText("High confidence"))
                .assert(hasText("Andrei drives, leaves at 08:30"))
                .assert(hasText("2 seats free"))
                .assert(hasText("Also good: Lake Weiss, 14:00–17:00"))
        }

    @Test
    fun `a NO-GO day gives the reason for each spot and the next good day`() =
        runComposeUiTest {
            show(TodayPreviewData.ready)

            onNodeWithTag("today.day.2026-10-22")
                .assert(hasText("NO-GO"))
                .assert(hasText("Today"))
                .assert(hasText("Too gusty"))
                .assert(hasText("Lake Weiss: Too gusty"))
                .assert(hasText("Nordstrand Bay: Offshore wind — dangerous here"))
                .assert(hasText("Next good day: Saturday"))
        }

    @Test
    fun `the header names the screen and says how old the forecast is`() =
        runComposeUiTest {
            show(TodayPreviewData.ready)

            onNode(isHeading() and hasText("Go?")).assertIsDisplayed()
            onNodeWithTag("today.header").assert(hasText("Updated 12 min ago"))
        }

    @Test
    fun `the disclaimer follows the answers`() =
        runComposeUiTest {
            show(TodayPreviewData.ready)

            onNodeWithTag("today.days").performScrollToNode(hasText("is not safety advice", substring = true))
            onNodeWithText("is not safety advice", substring = true).assertIsDisplayed()
        }

    @Test
    fun `offline the cached answers stay with a notice and a retry button`() =
        runComposeUiTest {
            show(TodayPreviewData.offline)

            onNodeWithTag("today.notice").assert(hasText("No connection. Showing the last saved forecast."))
            onNodeWithTag("today.header").assert(hasText("Updated 5 h ago"))
            onNodeWithTag("today.retry").performClick()

            assertEquals(listOf<TodayIntent>(TodayIntent.Refresh), intents)
        }

    @Test
    fun `an error without cached data offers a retry`() =
        runComposeUiTest {
            show(TodayPreviewData.error)

            onNodeWithText("Couldn’t load the forecast").assertIsDisplayed()
            onNodeWithText("No connection. Check your internet and try again.").assertIsDisplayed()
            onNodeWithTag("today.retry").performClick()

            assertEquals(listOf<TodayIntent>(TodayIntent.Refresh), intents)
        }

    @Test
    fun `a new rider is asked for a profile and a rider without favorites for spots`() =
        runComposeUiTest {
            var state by mutableStateOf(TodayPreviewData.noProfile)
            setContent { PursuitOfWindTheme(darkTheme = false) { TodayScreen(state = state, onIntent = {}) } }

            onNodeWithText("Tell us about you").assertIsDisplayed()
            state = TodayPreviewData.noSpots
            onNodeWithText("Pick your spots").assertIsDisplayed()
        }

    @Test
    fun `loading is announced to screen readers`() =
        runComposeUiTest {
            show(TodayPreviewData.loading)

            onNode(hasTestTag("today.loading") and hasContentDescription("Loading the forecast")).assertExists()
        }

    @Test
    fun `screen readers get a refresh action on the title and on every day instead of the pull gesture`() =
        runComposeUiTest {
            show(TodayPreviewData.ready)

            fun refreshOn(tag: String) =
                onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions]
                    .single { it.label == "Refresh" }

            refreshOn("today.header")
            refreshOn("today.day.2026-10-23")
            val onToday = refreshOn("today.day.2026-10-22")
            runOnIdle { onToday.action() }

            assertEquals(listOf<TodayIntent>(TodayIntent.Refresh), intents)
        }

    @Test
    fun `days and the title are headings so screen readers can jump between them`() =
        runComposeUiTest {
            show(TodayPreviewData.ready)

            onNodeWithTag("today.header").assert(isHeading())
            onNodeWithTag("today.day.2026-10-22").assert(isHeading())
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH_PX, PHONE_HEIGHT_PX), density = Density(PHONE_DENSITY)) {
            show(TodayPreviewData.ready, fontScale = 2f)

            val screenWidth = onRoot().fetchSemanticsNode().size.width
            val checked = mutableSetOf<String>()
            val cutOff = mutableSetOf<String>()
            // The list only lays out what is on screen, so bring each card in before measuring its text.
            listOf("today.day.2026-10-22", "today.day.2026-10-23", "today.day.2026-10-24").forEach { card ->
                onNodeWithTag("today.days").performScrollToNode(hasTestTag(card))
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
                        // Clipped vertically by its own box, or pushed past the edge of the screen.
                        if (layouts.any { it.didOverflowHeight } || node.boundsInRoot.left < 0f || node.boundsInRoot.right > screenWidth) {
                            cutOff += text
                        }
                    }
            }

            assertTrue("Andrei drives, leaves at 08:30" in checked, "The last card was never measured: $checked")
            assertEquals(emptySet(), cutOff)
        }

    @Test
    fun `russian speakers get the screen in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(TodayPreviewData.ready)
            scrollTo("today.day.2026-10-24")

            onNode(isHeading() and hasText("Едем?")).assertIsDisplayed()
            onNodeWithTag("today.header").assert(hasText("Обновлено 12 минут назад"))
            onNodeWithTag("today.day.2026-10-24")
                .assert(hasText("ДА"))
                .assert(hasText("Суббота"))
                .assert(hasText("18–22 уз"))
                .assert(hasText("Выезжай до 10:30"))
                .assert(hasText("2 свободных места"))
            onNodeWithTag("today.day.2026-10-22").assert(hasText("Ближайший хороший день: суббота"))
        }

    private companion object {
        // A 411 × 891 dp phone at 420 dpi.
        const val PHONE_WIDTH_PX = 1080f
        const val PHONE_HEIGHT_PX = 2340f
        const val PHONE_DENSITY = 2.625f
    }
}
