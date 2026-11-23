package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.link.AppLink
import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.NotificationPermission
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.data.repository.WindowAlertsRepository
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.testing.HeldRequests
import io.github.esseckers.pursuitofwind.testing.RecordingBackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.testing.RecordingNotificationPermission
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * The app's navigation with what comes in from the platform, on the real screens and the real data layer: links the
 * app was opened with, and the system's say on notifications behind the profile's alerts switch.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlatformNavigationTest {
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val joins = HeldRequests { it.method == HttpMethod.Post && it.url.encodedPath == "/v1/crews/join" }
    private val elsewhereForecast = HeldRequests { it.url.encodedPath == "/v1/spots/somewhere-else/forecast" }
    private val graph =
        testGraph(
            clock,
            module { single<HttpClientEngine> { joins.engine(elsewhereForecast.engine(get<FakePursuitOfWindServer>())) } },
        )
    private val links get() = graph.koin.get<PendingLinks>()
    private val viewModels = ViewModelStore()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = viewModels
        }
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun riderFollowingNordstrand() {
        // See AppNavigationTest: the desktop test runtime has no main dispatcher.
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
    fun `the alerts switch in the profile asks the system and its yes turns the alerts on`() =
        runComposeUiTest {
            val permission = graph.koin.get<NotificationPermission>() as RecordingNotificationPermission
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.profile").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.alerts")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("profile.alerts").performScrollTo().assertIsOff().performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.alerts") and isOn()).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, permission.requests)
            assertTrue(
                runBlocking {
                    graph.koin
                        .get<WindowAlertsRepository>()
                        .enabled
                        .first()
                },
            )
        }

    @Test
    fun `a no from the system stores no alerts and the profile leads to its settings and takes the wish back`() =
        runComposeUiTest {
            val permission = graph.koin.get<NotificationPermission>() as RecordingNotificationPermission
            permission.answer = false
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.profile").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.alerts")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("profile.alerts").performScrollTo().performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.alerts.settings")).fetchSemanticsNodes().isNotEmpty() }
            // The switch keeps showing what the rider asked for, so it can be taken back.
            onNodeWithTag("profile.alerts").assertIsOn()
            onNodeWithTag("profile.alerts.settings").performScrollTo().performClick()

            // The route opens the settings for an effect, which arrives a moment after the tap.
            waitUntil(timeoutMillis = TIMEOUT_MS) { permission.settingsOpened == 1 }
            onNodeWithTag("profile.alerts").performScrollTo().performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.alerts.settings")).fetchSemanticsNodes().isEmpty() }
            onNodeWithTag("profile.alerts").assertIsOff()
            assertEquals(1, permission.requests)
            assertFalse(
                runBlocking {
                    graph.koin
                        .get<WindowAlertsRepository>()
                        .enabled
                        .first()
                },
            )
        }

    @Test
    fun `a device that runs no background checks does not offer the alerts`() =
        runComposeUiTest {
            (graph.koin.get<BackgroundRefreshScheduler>() as RecordingBackgroundRefreshScheduler).available = false
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.profile").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.form")).fetchSemanticsNodes().isNotEmpty() }

            onNodeWithTag("profile.alerts").assertDoesNotExist()
        }

    @Test
    fun `an invitation link opens the Crew tab with its code in the join dialog and joins only when the rider confirms`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }

            assertTrue(graph.koin.get<PendingLinks>().open("pursuitofwind://invite/wind-4kq7"))

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("crew.joinDialog.field")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.crew").assertIsSelected()
            onNodeWithTag("crew.joinDialog.field").assert(hasText("wind-4kq7"))
            // Opening the link joined nothing.
            assertTrue(
                runBlocking {
                    graph.koin
                        .get<CrewRepository>()
                        .crews
                        .first()
                        .value
                        .isEmpty()
                },
            )
            onNodeWithTag("crew.joinDialog.confirm").performClick()

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(isHeading() and hasText("Weekend Crew")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(
                null,
                graph.koin
                    .get<PendingLinks>()
                    .link.value,
            )
        }

    @Test
    fun `a link to a spot opens its forecast at the day and back returns to Today`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.profile").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("profile.form")).fetchSemanticsNodes().isNotEmpty() }

            assertTrue(graph.koin.get<PendingLinks>().open("pursuitofwind://spot/nordstrand-bay?date=$SATURDAY"))

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.ready")).fetchSemanticsNodes().isNotEmpty() }
            onNode(isHeading() and hasText("Nordstrand Bay")).assertIsDisplayed()
            assertEquals(
                null,
                graph.koin
                    .get<PendingLinks>()
                    .link.value,
            )
            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab.today").assertIsSelected()
        }

    @Test
    fun `a link to a spot the rider does not follow shows nothing of it and fetches nothing`() =
        runComposeUiTest {
            showApp()

            assertTrue(links.open("pursuitofwind://spot/somewhere-else"))

            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("spot.missing")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
            // The screen decides whether to fetch before it shows anything; the rule itself is in SpotDetailRefreshPolicyTest.
            assertEquals(0, elsewhereForecast.sent)
        }

    @Test
    fun `an invitation opened on the Crew tab shows its code there and a second one takes its place`() =
        runComposeUiTest {
            showApp()
            onNodeWithTag("tab.crew").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("crew.join")).fetchSemanticsNodes().isNotEmpty() }

            assertTrue(links.open("pursuitofwind://invite/WIND-4KQ7"))
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("crew.joinDialog.field")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("crew.joinDialog.field").assert(hasText("WIND-4KQ7"))

            assertTrue(links.open("pursuitofwind://invite/OWLS-77"))
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasTestTag("crew.joinDialog.field") and hasText("OWLS-77")).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(null, links.link.value)
        }

    @Test
    fun `an invitation opened while a crew is being joined waits for that to end`() =
        runComposeUiTest {
            showApp()
            onNodeWithTag("tab.crew").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("crew.join")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("crew.join").performScrollTo().performClick()
            onNodeWithTag("crew.joinDialog.field").performTextInput("WIND-4KQ7")
            joins.hold()
            onNodeWithTag("crew.joinDialog.confirm").performClick()
            runBlocking { joins.awaitHeld() }

            assertTrue(links.open("pursuitofwind://invite/OWLS-77"))
            waitForIdle()
            // The dialog that is sending stays as it is, and the invitation is not used up.
            onNodeWithTag("crew.joinDialog.field").assert(hasText("WIND-4KQ7"))
            assertEquals(AppLink.Invite("OWLS-77"), links.link.value)

            joins.release()
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasTestTag("crew.joinDialog.field") and hasText("OWLS-77")).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(null, links.link.value)
        }

    private fun ComposeUiTest.showApp() {
        setContent {
            KoinIsolatedContext(graph) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
            }
        }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag("today.header")).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        const val TIMEOUT_MS = 10_000L
    }
}
