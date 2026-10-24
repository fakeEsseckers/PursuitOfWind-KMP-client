package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.testing.RecordingShareLauncher
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinIsolatedContext
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * The app's navigation with its real screens on the real data layer: Today opens a spot's forecast at the tapped day,
 * and back returns to Today.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class AppNavigationTest {
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val graph = testGraph(clock)
    private val viewModels = ViewModelStore()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = viewModels
        }
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun riderFollowingNordstrand() {
        // The desktop test runtime has no main dispatcher. Unconfined runs ViewModel work on the thread that resumes it,
        // and counts every thread as the main one, which is what lifecycle checks when the navigation moves entries.
        // It gives up the app's one-main-thread guarantee, which is fine for one user flow but not for racing intents.
        Dispatchers.setMain(Dispatchers.Unconfined)
        Locale.setDefault(Locale.ENGLISH)
        runBlocking {
            graph.koin
                .get<ProfileRepository>()
                .save(alex())
                .value()
            graph.koin
                .get<SpotRepository>()
                .follow(NORDSTRAND, 2.hours, favorite = true)
                .value()
        }
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        graph.close()
        releaseMainDispatcher()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `a day on Today opens the forecast of its spot and back returns`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            val saturday = "today.day.$SATURDAY"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(saturday)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("today.days").performScrollToNode(hasTestTag(saturday))
            onNodeWithTag(saturday).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
            onNode(isHeading() and hasText("Nordstrand Bay")).assertIsDisplayed()
            // Opened at the day that was tapped.
            onNodeWithTag("spot.day.$SATURDAY").assertIsSelected()
            onNodeWithTag("spot.details").assert(hasText("Today", substring = true))

            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("today.header").assertIsDisplayed()
        }

    @Test
    fun `the Profile tab shows the saved profile and the Today tab comes back`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.today").assertIsSelected()
            onNodeWithTag("tab.profile").performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.form")).fetchSemanticsNodes().isNotEmpty() }
            onNode(isHeading() and hasText("Profile")).assertIsDisplayed()
            onNodeWithTag("profile.name").assert(hasText("Alex"))
            onNodeWithTag("tab.profile").assertIsSelected()

            onNodeWithTag("tab.today").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.today").assertIsSelected()
        }

    @Test
    fun `a rider without a favorite picks one from Today and gets an answer`() =
        runComposeUiTest {
            runBlocking {
                graph.koin
                    .get<SpotRepository>()
                    .follow(NORDSTRAND, 2.hours, favorite = false)
                    .value()
            }
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.pickSpots")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("today.pickSpots").performClick()

            val nordstrand = "spots.toggle.nordstrand-bay"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(nordstrand)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("spots.list").performScrollToNode(hasTestTag(nordstrand))
            onNodeWithTag(nordstrand).performClick()
            onNodeWithTag("spots.done").performClick()

            val saturday = "today.day.$SATURDAY"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(saturday)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.today").assertIsSelected()
        }

    @Test
    fun `a language picked in the profile switches the whole app and keeps the screen and the draft`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.profile").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.form")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("profile.weight").performTextClearance()
            onNodeWithTag("profile.weight").performTextInput("78")
            onNodeWithTag("profile.language.RUSSIAN").performScrollTo().performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(isHeading() and hasText("Профиль")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.profile").assertIsSelected()
            onNodeWithTag("tab.today").assert(hasText("Сегодня"))
            onNodeWithTag("profile.weight").assert(hasText("78"))
        }

    @Test
    fun `spots added from the Spots tab show there and a spot's forecast returns to the tab`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").performClick()
            waitUntil(
                timeoutMillis = TIMEOUT_MS,
            ) { onAllNodes(hasTestTag("myspots.card.nordstrand-bay")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("myspots.add").performClick()

            val salt = "spots.toggle.salt-lagoon"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(salt)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("spots.list").performScrollToNode(hasTestTag(salt))
            onNodeWithTag(salt).performClick()
            onNodeWithTag("spots.done").performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.card.salt-lagoon")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").assertIsSelected()
            onNodeWithTag("myspots.list").performScrollToNode(hasTestTag("myspots.open.nordstrand-bay"))
            onNodeWithTag("myspots.open.nordstrand-bay").performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.list")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").assertIsSelected()
        }

    @Test
    fun `a rider joins the Weekend Crew from the Crew tab takes a seat and invites a friend`() =
        runComposeUiTest {
            showApp()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.crew").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("crew.none")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("crew.join").performClick()
            onNodeWithTag("crew.joinDialog.field").performTextInput("wind-4kq7")
            onNodeWithTag("crew.joinDialog.confirm").performClick()

            val ride = "crew.ride.rid_7f3c"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(ride)).fetchSemanticsNodes().isNotEmpty() }
            onNode(isHeading() and hasText("Weekend Crew")).assertIsDisplayed()
            onNodeWithTag("crew.list").performScrollToNode(hasTestTag(ride))
            onNodeWithTag("crew.action.rid_7f3c").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasContentDescription("Leave the ride to Nordstrand Bay")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("crew.list").performScrollToNode(hasTestTag("crew.share"))
            onNodeWithTag("crew.share").performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { (graph.koin.get<ShareLauncher>() as RecordingShareLauncher).shared.isNotEmpty() }
            assertEquals(
                listOf("Join my kite crew “Weekend Crew” in Pursuit Of Wind with the invite code WIND-4KQ7."),
                (graph.koin.get<ShareLauncher>() as RecordingShareLauncher).shared,
            )
        }

    @Test
    fun `Invite friends on the snackbar of a new crew shares that crew even after switching to another`() =
        runComposeUiTest {
            runBlocking {
                graph.koin
                    .get<CrewRepository>()
                    .join("WIND-4KQ7")
                    .value()
            }
            showApp()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.crew").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(isHeading() and hasText("Weekend Crew")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("crew.list").performScrollToNode(hasTestTag("crew.create"))
            onNodeWithTag("crew.create").performClick()
            onNodeWithTag("crew.createDialog.field").performTextInput("Night Owls")
            onNodeWithTag("crew.createDialog.confirm").performClick()

            val snackbarAction = hasText("Invite friends") and !hasTestTag("crew.share")
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(snackbarAction).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("crew.choose.crw_weekend").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(isHeading() and hasText("Weekend Crew")).fetchSemanticsNodes().isNotEmpty() }
            onNode(snackbarAction).performClick()

            val launcher = graph.koin.get<ShareLauncher>() as RecordingShareLauncher
            waitUntil(timeoutMillis = TIMEOUT_MS) { launcher.shared.isNotEmpty() }
            val shared = launcher.shared.single()
            assertTrue("“Night Owls”" in shared, shared)
            assertFalse("WIND-4KQ7" in shared, shared)
        }

    @Test
    fun `I'm going on a GO day offers the crew a ride that Today and the Crew tab then show`() =
        runComposeUiTest {
            runBlocking {
                graph.koin
                    .get<CrewRepository>()
                    .join("WIND-4KQ7")
                    .value()
            }
            showApp()

            val offer = "today.offer.$SATURDAY"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(offer)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("today.days").performScrollToNode(hasTestTag(offer))
            onNodeWithTag(offer).performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("offer.summary")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tabs").assertDoesNotExist()
            onNodeWithTag("offer.meeting").performTextInput("Old harbour car park")
            onNodeWithTag("offer.submit").performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.days")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("today.days").performScrollToNode(hasTestTag("today.day.$SATURDAY"))
            // Saturday's time to leave has passed at 08:00, so the ride leaves at the next quarter hour.
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("You drive, leaving at 08:15", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(offer).assertDoesNotExist()
            onNodeWithTag("tab.crew").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasContentDescription("Cancel the ride to Nordstrand Bay")).fetchSemanticsNodes().isNotEmpty()
            }
        }

    private fun androidx.compose.ui.test.ComposeUiTest.showApp() {
        setContent {
            KoinIsolatedContext(graph) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
            }
        }
    }

    @Test
    fun `a screen opened from a tab hides the tabs`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            val saturday = "today.day.$SATURDAY"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(saturday)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tabs").assertIsDisplayed()
            onNodeWithTag("today.days").performScrollToNode(hasTestTag(saturday))
            onNodeWithTag(saturday).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tabs").assertDoesNotExist()
        }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        const val TIMEOUT_MS = 10_000L
    }
}
