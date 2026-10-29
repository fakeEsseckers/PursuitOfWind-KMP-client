package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
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
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.esseckers.pursuitofwind.app.language.AppLocale
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher
import io.github.esseckers.pursuitofwind.core.platform.TextClipboard
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.testing.RecordingAppLocale
import io.github.esseckers.pursuitofwind.testing.RecordingShareLauncher
import io.github.esseckers.pursuitofwind.testing.RecordingTextClipboard
import io.github.esseckers.pursuitofwind.testing.ScriptedSpotMap
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
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
            // The phone's language first, then the one picked.
            assertEquals(listOf(null, "ru"), (graph.koin.get<AppLocale>() as RecordingAppLocale).tags)
        }

    @Test
    fun `on a phone spots added from the Spots tab show there and a spot's forecast returns to the tab`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
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
    fun `the coordinates of a spot in the picker go to the clipboard and a snackbar says so`() =
        runComposeUiTest {
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.add")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("myspots.add").performClick()

            val copy = "spots.copy.salt-lagoon"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(copy)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("spots.list").performScrollToNode(hasTestTag(copy))
            onNodeWithTag(copy).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Coordinates copied")).fetchSemanticsNodes().isNotEmpty() }
            val clipboard = graph.koin.get<TextClipboard>() as RecordingTextClipboard
            assertEquals(listOf("Coordinates" to "53.07120, 8.21560"), clipboard.copied)
        }

    @Test
    fun `where the system shows what was copied the picker says nothing more`() =
        runComposeUiTest {
            val clipboard = graph.koin.get<TextClipboard>() as RecordingTextClipboard
            clipboard.systemShows = true
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.add")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("myspots.add").performClick()

            val copy = "spots.copy.dune-point"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(copy)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("spots.list").performScrollToNode(hasTestTag(copy))
            onNodeWithTag(copy).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { clipboard.copied.isNotEmpty() }
            waitForIdle()
            assertEquals(listOf("Coordinates" to "55.01570, 8.43520"), clipboard.copied)
            onNode(hasText("Coordinates copied")).assertDoesNotExist()
        }

    @Test
    fun `on a phone a spot added on the map from the picker comes back chosen and shows on the Spots tab`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.add")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("myspots.add").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spots.map")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("spots.map").performClick()

            val map = graph.koin.get<SpotMap>() as ScriptedSpotMap
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("map")).fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { map.longPress(GeoPoint(54.40, 8.60)) }
            runOnIdle { map.tap(GeoPoint(54.40, 8.597)) }
            onNodeWithTag("spotmap.name").performScrollTo().performTextInput("Sand Spit")
            onNodeWithTag("spotmap.save").performScrollTo().performClick()

            // Back in the picker once the spot is added and followed, with the new spot chosen.
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasTestTag("spotmap.name")).fetchSemanticsNodes().isEmpty() &&
                    onAllNodes(hasTestTag("spots.list")).fetchSemanticsNodes().isNotEmpty()
            }
            val added =
                runBlocking {
                    graph.koin
                        .get<SpotRepository>()
                        .mySpots
                        .first()
                        .value
                        .single { it.spot.name == "Sand Spit" }
                }
            val toggle = "spots.toggle.${added.spot.id.value}"
            onNodeWithTag("spots.list").performScrollToNode(hasTestTag(toggle))
            onNodeWithTag(toggle).assertIsOn()
            onNodeWithTag("spots.done").performClick()

            val card = "myspots.card.${added.spot.id.value}"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(card)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").assertIsSelected()
        }

    @Test
    fun `on a phone a spot the rider added is changed on the map from the Spots tab`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
            val mine =
                runBlocking {
                    val spots = graph.koin.get<SpotRepository>()
                    val added = spots.addSpot("Sand Spit", GeoPoint(54.40, 8.60), Bearing(270.0), TimeZone.of("Europe/Berlin")).value()
                    spots.follow(added.id, 1.hours, favorite = true).value()
                    added.id.value
                }
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").performClick()
            val change = "myspots.change.$mine"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(change)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("myspots.list").performScrollToNode(hasTestTag(change))
            onNodeWithTag(change).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spotmap.name")).fetchSemanticsNodes().isNotEmpty() }
            onNode(isHeading() and hasText("Change the spot")).assertIsDisplayed()
            onNodeWithTag("spotmap.name").performScrollTo().performTextClearance()
            onNodeWithTag("spotmap.name").performTextInput("North Spit")
            onNodeWithTag("spotmap.save").performScrollTo().performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasTestTag("myspots.card.$mine") and hasAnyDescendant(hasText("North Spit"))).fetchSemanticsNodes().isNotEmpty()
            }
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
            onNodeWithTag("tab.today").assertDoesNotExist()
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

    @Test
    fun `on a phone the tabs are a bar along the bottom`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }

            val today = onNodeWithTag("tab.today").getUnclippedBoundsInRoot()
            val profile = onNodeWithTag("tab.profile").getUnclippedBoundsInRoot()
            assertEquals(today.top, profile.top)
            assertTrue(profile.left >= today.right, "$today $profile")
            assertTrue(today.top > (PHONE_HEIGHT / 2).dp, today.toString())
        }

    @Test
    fun `a tab keeps its layout wherever it is placed as during a slide`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
            // A slide moves the tab sideways a little every frame. What the tab keeps clear of must not follow its
            // position, or its content would squeeze and reflow on every frame of the slide.
            setContent {
                Box(modifier = Modifier.offset(x = (-SLIDE).dp)) {
                    KoinIsolatedContext(graph) {
                        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                    }
                }
            }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }

            // The header's own margins, and nothing for where it is.
            val header = onNodeWithTag("today.header").getUnclippedBoundsInRoot()
            assertEquals(Spacing.m - SLIDE.dp, header.left)
            assertEquals(PHONE_WIDTH.dp - Spacing.m * 2, header.right - header.left)
        }

    @Test
    fun `on a tablet the tabs are a rail along the start`() =
        runSkikoComposeUiTest(size = Size(TABLET_WIDTH, TABLET_HEIGHT)) {
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }

            val today = onNodeWithTag("tab.today").getUnclippedBoundsInRoot()
            val profile = onNodeWithTag("tab.profile").getUnclippedBoundsInRoot()
            assertEquals(today.left, profile.left)
            assertTrue(profile.top >= today.bottom, "$today $profile")
            assertTrue(today.right < (TABLET_WIDTH / 2).dp, today.toString())
            // The tab lays out beside the rail.
            assertTrue(onNodeWithTag("today.header").getUnclippedBoundsInRoot().left >= today.right)
        }

    @Test
    fun `on a tablet a spot opened from Spots shows beside the list and the next one takes its place`() =
        runSkikoComposeUiTest(size = Size(TABLET_WIDTH, TABLET_HEIGHT)) {
            runBlocking {
                graph.koin
                    .get<SpotRepository>()
                    .follow(LAKE_WEISS, 1.hours, favorite = false)
                    .value()
            }
            showApp()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").performClick()
            // Until a spot is opened, the pane beside the list says where its forecast will show.
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.noSpot")).fetchSemanticsNodes().isNotEmpty() }

            openFromList("nordstrand-bay")
            waitUntil(timeoutMillis = TIMEOUT_MS) { forecastOf("Nordstrand Bay") }
            onNodeWithTag("myspots.list").assertIsDisplayed()
            onNodeWithTag("myspots.noSpot").assertDoesNotExist()
            val list = onNodeWithTag("myspots.list").getUnclippedBoundsInRoot()
            assertTrue(list.right <= onNodeWithTag("spot.ready").getUnclippedBoundsInRoot().left)

            openFromList("lake-weiss")
            waitUntil(timeoutMillis = TIMEOUT_MS) { forecastOf("Lake Weiss") && !forecastOf("Nordstrand Bay") }

            // The second forecast took the first one's place, so back returns to the list.
            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.noSpot")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("spot.ready").assertDoesNotExist()
            onNodeWithTag("tab.spots").assertIsSelected()
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
            onNodeWithTag("tab.today").assertIsDisplayed()
            onNodeWithTag("today.days").performScrollToNode(hasTestTag(saturday))
            onNodeWithTag(saturday).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.today").assertDoesNotExist()
        }

    @Test
    fun `on a tablet system back closes the forecast beside the list and then returns from the tab to Today`() =
        runSkikoComposeUiTest(size = Size(TABLET_WIDTH, TABLET_HEIGHT)) {
            val back = SystemBack()
            showApp(back)

            openNordstrandFromSpots()
            onNodeWithTag("myspots.list").assertIsDisplayed()
            runOnIdle { back.input.backCompleted() }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isEmpty() }
            onNodeWithTag("myspots.list").assertIsDisplayed()

            runOnIdle { back.input.backCompleted() }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.list")).fetchSemanticsNodes().isEmpty() }
            onNodeWithTag("today.header").assertIsDisplayed()
        }

    @Test
    fun `on a phone a predictive back from a spot's forecast previews the Spots tab with its tabs`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
            val back = SystemBack()
            showApp(back)
            openNordstrandFromSpots()
            // One screen at a time on a phone: the forecast covers the list.
            onNodeWithTag("myspots.list").assertDoesNotExist()

            runOnIdle {
                back.input.backStarted(NavigationEvent(progress = 0f))
                back.input.backProgressed(NavigationEvent(progress = 0.5f))
            }
            waitForIdle()
            // Halfway through the gesture both screens show, and the tab below already has its tabs.
            assertTrue(onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodes(hasTestTag("myspots.list")).fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodes(hasTestTag("tab.spots")).fetchSemanticsNodes().isNotEmpty())

            runOnIdle { back.input.backCompleted() }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isEmpty() }
            onNodeWithTag("myspots.list").assertIsDisplayed()
        }

    @Test
    fun `on a phone the screens on the back stack come back after the app's state is restored`() =
        runSkikoComposeUiTest(size = Size(PHONE_WIDTH, PHONE_HEIGHT)) {
            restoreWithNordstrandOpen()

            onNodeWithTag("myspots.list").assertDoesNotExist()
            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.list")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.spots").assertIsSelected()
        }

    @Test
    fun `on a tablet a forecast opened from the list comes back beside it after the app's state is restored`() =
        runSkikoComposeUiTest(size = Size(TABLET_WIDTH, TABLET_HEIGHT)) {
            restoreWithNordstrandOpen()

            // The key still says it was opened from the list, so it shows beside it again.
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.list")).fetchSemanticsNodes().isNotEmpty() }
            val list = onNodeWithTag("myspots.list").getUnclippedBoundsInRoot()
            assertTrue(list.right <= onNodeWithTag("spot.ready").getUnclippedBoundsInRoot().left)
        }

    @Test
    fun `on a tablet a forecast opened from Today is a screen of its own`() =
        runSkikoComposeUiTest(size = Size(TABLET_WIDTH, TABLET_HEIGHT)) {
            showApp()
            val saturday = "today.day.$SATURDAY"
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(saturday)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(saturday).performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.today").assertDoesNotExist()
            val forecast = onNodeWithTag("spot.ready").getUnclippedBoundsInRoot()
            assertTrue(forecast.left < (TABLET_WIDTH / 4).dp && forecast.right > (TABLET_WIDTH * 3 / 4).dp, forecast.toString())
        }

    @Test
    fun `on a phone in landscape the tabs are a bar and a spot's forecast covers the list`() =
        runSkikoComposeUiTest(size = Size(LANDSCAPE_PHONE_WIDTH, LANDSCAPE_PHONE_HEIGHT)) {
            showApp()
            openNordstrandFromSpots()

            // Wide enough for two panes, but short: the tabs are a bar, and one screen shows at a time.
            onNodeWithTag("myspots.list").assertDoesNotExist()
            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("myspots.list")).fetchSemanticsNodes().isNotEmpty() }
            val today = onNodeWithTag("tab.today").getUnclippedBoundsInRoot()
            assertEquals(today.top, onNodeWithTag("tab.profile").getUnclippedBoundsInRoot().top)
        }

    /** Opens the forecast of [spotId] from the Spots list. */
    private fun ComposeUiTest.openFromList(spotId: String) {
        val open = "myspots.open.$spotId"
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(open)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("myspots.list").performScrollToNode(hasTestTag(open))
        onNodeWithTag(open).performClick()
    }

    /** True while a forecast of the spot named [name] is on screen. */
    private fun ComposeUiTest.forecastOf(name: String): Boolean =
        onAllNodes(hasAnyAncestor(hasTestTag("spot.header")) and hasText(name, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** The app on the test graph; [systemBack], when given, is where system back comes from. */
    private fun ComposeUiTest.showApp(systemBack: SystemBack? = null) {
        setContent {
            KoinIsolatedContext(graph) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    if (systemBack == null) {
                        PursuitOfWindTheme { AppNavigation() }
                    } else {
                        CompositionLocalProvider(
                            LocalNavigationEventDispatcherOwner provides systemBack,
                        ) { PursuitOfWindTheme { AppNavigation() } }
                    }
                }
            }
        }
    }

    /**
     * Opens Nordstrand Bay's forecast from the Spots tab, then restores the app's saved state as after process death.
     * `StateRestorationTester` does not run on desktop, so this saves the registry, disposes the app with its ViewModels
     * and composes it again. The registry takes any value, so this checks the back stack's round trip through its
     * serializers, not what fits in a Bundle; the Koin graph and its data stay, as the database and DataStore would.
     */
    private fun ComposeUiTest.restoreWithNordstrandOpen() {
        var registry by mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })
        var shown by mutableStateOf(true)
        setContent {
            if (shown) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    KoinIsolatedContext(graph) {
                        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                    }
                }
            }
        }

        openNordstrandFromSpots()
        val saved = runOnIdle { registry.performSave() }
        shown = false
        waitForIdle()
        runOnIdle { viewModels.clear() }
        registry = SaveableStateRegistry(restoredValues = saved) { true }
        shown = true
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** From Today to the Spots tab, then Nordstrand Bay's forecast. */
    private fun ComposeUiTest.openNordstrandFromSpots() {
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tab.spots").performClick()
        val open = "myspots.open.nordstrand-bay"
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(open)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("myspots.list").performScrollToNode(hasTestTag(open))
        onNodeWithTag(open).performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** System back as Android and iOS send it: through the navigation event dispatcher, not a screen's back button. */
    private class SystemBack : NavigationEventDispatcherOwner {
        val input = DirectNavigationEventInput()
        override val navigationEventDispatcher = NavigationEventDispatcher().apply { addInput(input) }
    }

    private companion object {
        val LAKE_WEISS = SpotId("lake-weiss")

        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        const val TIMEOUT_MS = 10_000L

        /** How far a tab is moved in the slide test, in dp. */
        const val SLIDE = 100f

        /** Window sizes in dp (the test density is 1): a compact width, and an expanded one. */
        const val PHONE_WIDTH = 400f
        const val PHONE_HEIGHT = 800f
        const val TABLET_WIDTH = 1280f
        const val TABLET_HEIGHT = 800f

        /** Wide enough for two panes, but short: a phone in landscape. */
        const val LANDSCAPE_PHONE_WIDTH = 900f
        const val LANDSCAPE_PHONE_HEIGHT = 400f
    }
}
