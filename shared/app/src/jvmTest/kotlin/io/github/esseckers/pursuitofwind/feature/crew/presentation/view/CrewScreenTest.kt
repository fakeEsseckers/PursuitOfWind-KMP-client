package io.github.esseckers.pursuitofwind.feature.crew.presentation.view

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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.alex
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.andreisRide
import io.github.esseckers.pursuitofwind.feature.crew.presentation.preview.CrewPreviewData.details
import io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel.CrewReducer
import io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel.CrewResult
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.checkTexts
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Crew tab on the desktop runtime: what each state shows, what its controls send, and how it reads. */
@OptIn(ExperimentalTestApi::class)
class CrewScreenTest {
    private val intents = mutableListOf<CrewIntent>()
    private val systemLocale = Locale.getDefault()

    /** Alex has a seat in Andrei's car. */
    private val seated =
        CrewReducer(CrewPreviewData.zone).reduce(
            CrewPreviewData.ready,
            CrewResult.DetailsChanged(
                details.copy(
                    rides =
                        Cached(
                            details.rides.value.map {
                                if (it ==
                                    andreisRide
                                ) {
                                    it.copy(passengers = it.passengers + alex, seatsFree = 0)
                                } else {
                                    it
                                }
                            },
                            null,
                        ),
                ),
            ),
        )

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    private fun ComposeUiTest.show(
        state: CrewState,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(darkTheme = false) { CrewScreen(state = state, onIntent = { intents += it }) }
            }
        }
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag("crew.list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `the crew reads as headings with its invite code its rides by day and its members`() =
        runComposeUiTest {
            show(CrewPreviewData.ready)

            onNode(isHeading() and hasText("Crew")).assertIsDisplayed()
            onNode(isHeading() and hasText("Weekend Crew")).assertIsDisplayed()
            onNode(hasText("Invite code: WIND-4KQ7")).assertIsDisplayed()
            onNode(isHeading() and hasText("Tomorrow")).assertExists()
            scrollTo("crew.ride.rid_andrei")
            onNodeWithTag("crew.ride.rid_andrei").assert(hasAnyDescendant(isHeading() and hasText("Nordstrand Bay")))
            onNodeWithTag("crew.ride.rid_andrei").assert(hasAnyDescendant(hasText("Andrei drives, leaves at 10:30")))
            onNodeWithTag("crew.ride.rid_andrei").assert(hasAnyDescendant(hasText("Window 13:00–17:00")))
            onNodeWithTag("crew.ride.rid_andrei").assert(hasAnyDescendant(hasText("With Mira")))
            onNodeWithTag("crew.seats.rid_andrei").assert(hasText("1 seat free"))
            onNodeWithTag("crew.action.rid_andrei").assert(hasContentDescription("Take a seat to Nordstrand Bay"))
            scrollTo("crew.members")
            onNodeWithTag("crew.members").assert(hasAnyDescendant(hasText("Alex (you)")))
            onNodeWithTag("crew.members").assert(hasAnyDescendant(hasText("founder")))
        }

    @Test
    fun `each ride's button sends what it offers and the forecast link opens the spot at that day`() =
        runComposeUiTest {
            show(seated)

            scrollTo("crew.ride.rid_alex")
            onNodeWithTag("crew.action.rid_alex").assert(hasContentDescription("Cancel the ride to Lake Weiss")).performClick()
            scrollTo("crew.ride.rid_andrei")
            onNodeWithTag("crew.ride.rid_andrei").assert(hasAnyDescendant(hasText("With Mira, you")))
            onNodeWithTag("crew.action.rid_andrei").assert(hasContentDescription("Leave the ride to Nordstrand Bay")).performClick()
            onNodeWithTag("crew.open.rid_andrei").performClick()

            assertEquals(
                listOf(
                    CrewIntent.CancelRide("rid_alex"),
                    CrewIntent.LeaveRide("rid_andrei"),
                    CrewIntent.OpenSpot(CrewPreviewData.nordstrand.id, LocalDate(2026, 10, 24)),
                ),
                intents,
            )
        }

    @Test
    fun `a free seat can be taken while a full car and a cancelled ride offer nothing`() =
        runComposeUiTest {
            show(CrewPreviewData.ready)

            scrollTo("crew.ride.rid_andrei")
            onNodeWithTag("crew.action.rid_andrei").performClick()
            scrollTo("crew.ride.rid_jonas")
            onNodeWithTag("crew.seats.rid_jonas").assert(hasText("No seats left"))
            onNodeWithTag("crew.action.rid_jonas").assertDoesNotExist()
            scrollTo("crew.ride.rid_mira")
            onNodeWithTag("crew.seats.rid_mira").assert(hasText("Cancelled"))
            onNodeWithTag("crew.action.rid_mira").assertDoesNotExist()

            assertEquals(listOf<CrewIntent>(CrewIntent.TakeSeat("rid_andrei")), intents)
        }

    @Test
    fun `a seat waiting for the server says so and its button takes no taps`() =
        runComposeUiTest {
            show(CrewPreviewData.takingSeat)

            scrollTo("crew.ride.rid_andrei")
            onNodeWithTag("crew.seats.rid_andrei").assert(hasText("Saving…"))
            onNodeWithTag("crew.action.rid_andrei").assertIsNotEnabled()
        }

    @Test
    fun `chips switch crews the header shares the invite and more crews are at the end`() =
        runComposeUiTest {
            show(CrewPreviewData.ready)

            onNodeWithTag("crew.choose.crw_owls").performClick()
            onNodeWithTag("crew.share").assert(hasText("Invite friends")).performClick()
            scrollTo("crew.join")
            onNodeWithTag("crew.create").performClick()
            onNodeWithTag("crew.join").performClick()

            assertEquals(
                listOf(CrewIntent.ChooseCrew("crw_owls"), CrewIntent.ShareInvite, CrewIntent.StartCreate, CrewIntent.StartJoin),
                intents,
            )
        }

    @Test
    fun `with no crew the message offers to create one or join with a code`() =
        runComposeUiTest {
            show(CrewPreviewData.noCrew)

            onNodeWithTag("crew.none").assert(hasAnyDescendant(hasText("Ride with your crew")))
            onNodeWithTag("crew.create").performClick()
            onNodeWithTag("crew.join").performClick()

            assertEquals(listOf(CrewIntent.StartCreate, CrewIntent.StartJoin), intents)
        }

    @Test
    fun `the create dialog sends the name once there is one`() =
        runComposeUiTest {
            show(CrewPreviewData.creating)

            onNodeWithTag("crew.createDialog.confirm").assertIsNotEnabled()
            onNodeWithTag("crew.createDialog.field").performTextInput("Night Owls")
            onNodeWithTag("crew.createDialog.confirm").assertIsEnabled().performClick()
            onNodeWithTag("crew.createDialog.close").performClick()

            assertEquals(listOf(CrewIntent.CreateCrew("Night Owls"), CrewIntent.CloseDialog), intents)
        }

    @Test
    fun `the join dialog says why the code failed and sends the next one`() =
        runComposeUiTest {
            show(CrewPreviewData.joinFailed)

            onNodeWithTag("crew.joinDialog").assert(hasAnyDescendant(hasText("No crew has this code. Check it with whoever sent it.")))
            onNodeWithTag("crew.joinDialog.field").performTextInput("WIND-4KQ7")
            onNodeWithTag("crew.joinDialog.confirm").performClick()

            assertEquals(listOf<CrewIntent>(CrewIntent.JoinCrew("WIND-4KQ7")), intents)
        }

    @Test
    fun `cancelling a ride asks first and says who loses a seat`() =
        runComposeUiTest {
            show(CrewPreviewData.confirmingCancel)

            onNodeWithTag("crew.cancelDialog").assert(hasAnyDescendant(hasText("Cancel the ride to Lake Weiss?")))
            onNodeWithTag(
                "crew.cancelDialog",
            ).assert(hasAnyDescendant(hasText("1 rider loses their seat. Your crew sees it was cancelled.")))
            onNodeWithTag("crew.cancelDialog.confirm").performClick()
            onNodeWithTag("crew.cancelDialog.keep").performClick()

            assertEquals(listOf(CrewIntent.ConfirmCancel, CrewIntent.CloseDialog), intents)
        }

    @Test
    fun `offline the saved crew stays with a notice and errors offer a retry`() =
        runComposeUiTest {
            show(CrewPreviewData.offline)

            onNodeWithTag("crew.notice").assert(hasText("No connection. Showing what was saved."))
            onNodeWithTag("crew.retry").performClick()

            assertEquals(listOf<CrewIntent>(CrewIntent.Refresh), intents)
        }

    @Test
    fun `nothing cached and no connection offers a retry`() =
        runComposeUiTest {
            show(CrewPreviewData.error)

            onNodeWithTag("crew.error").assert(hasAnyDescendant(hasText("Couldn’t load your crew")))
            onNodeWithTag("crew.retry").performClick()

            assertEquals(listOf<CrewIntent>(CrewIntent.Refresh), intents)
        }

    @Test
    fun `loading is announced and screen readers can refresh from the title`() =
        runComposeUiTest {
            show(CrewPreviewData.loading)

            onNode(hasTestTag("crew.loading") and hasContentDescription("Loading your crew")).assertExists()
            val refresh =
                onNode(isHeading() and hasText("Crew"))
                    .fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions]
                    .single { it.label == "Refresh" }
            runOnIdle { refresh.action() }

            assertEquals(listOf<CrewIntent>(CrewIntent.Refresh), intents)
        }

    @Test
    fun `russian speakers get the tab in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(CrewPreviewData.ready)

            onNode(isHeading() and hasText("Компания")).assertIsDisplayed()
            onNodeWithTag("crew.share").assert(hasText("Позвать друзей"))
            scrollTo("crew.ride.rid_andrei")
            onNodeWithTag("crew.action.rid_andrei").assert(hasContentDescription("Занять место в поездке на Nordstrand Bay"))
            onNodeWithTag("crew.seats.rid_andrei").assert(hasText("1 свободное место"))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(CrewPreviewData.ready, fontScale = 2f)

            val checked = mutableSetOf<String>()
            val cutOff = mutableSetOf<String>()
            // The list only lays out what is on screen, so bring each part in before measuring its text.
            listOf(
                "crew.share",
                "crew.ride.rid_mira",
                "crew.ride.rid_alex",
                "crew.ride.rid_andrei",
                "crew.ride.rid_jonas",
                "crew.members",
                "crew.join",
            ).forEach { tag ->
                scrollTo(tag)
                val fit = checkTexts()
                checked += fit.checked
                cutOff += fit.cutOff
            }

            assertTrue("Join with a code" in checked, "The end of the list was never measured: $checked")
            assertEquals(emptySet(), cutOff)
        }
}
