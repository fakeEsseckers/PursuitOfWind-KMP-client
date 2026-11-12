package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.link.AppLink
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinIsolatedContext
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A new rider's first launch on the real screens and data layer: Welcome, the profile, the spots, and Today with an
 * answer, the whole way through the in-app fake backend.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class OnboardingNavigationTest {
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val graph = testGraph(clock)
    private val viewModels = ViewModelStore()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = viewModels
        }
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        // See AppNavigationTest: the desktop test runtime has no main dispatcher.
        Dispatchers.setMain(Dispatchers.Unconfined)
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        graph.close()
        releaseMainDispatcher()
        Locale.setDefault(systemLocale)
    }

    private fun ComposeUiTest.waitFor(tag: String) {
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a new rider goes from welcome through the profile and the spots to an answer on Today`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitFor("welcome")
            onNodeWithTag("tab.today").assertDoesNotExist()
            onNodeWithTag("welcome.start").performScrollTo().performClick()

            waitFor("profile.form")
            onNodeWithTag("profile.name").performTextInput("Mira")
            onNodeWithTag("profile.weight").performTextInput("70")
            onNodeWithTag("profile.save").performScrollTo().performClick()

            waitFor("spots.toggle.nordstrand-bay")
            onNodeWithTag("spots.list").performScrollToNode(hasTestTag("spots.toggle.nordstrand-bay"))
            onNodeWithTag("spots.toggle.nordstrand-bay").performClick()
            onNodeWithTag("spots.done").performClick()

            val saturday = "today.day.$SATURDAY"
            waitFor(saturday)
            onNodeWithTag("today.header").assertIsDisplayed()
            onNodeWithTag("tab.today").assertIsSelected()

            runBlocking {
                assertEquals(
                    "Mira",
                    graph.koin
                        .get<ProfileRepository>()
                        .profile
                        .first()
                        .value
                        ?.displayName,
                )
                val followed =
                    graph.koin
                        .get<SpotRepository>()
                        .mySpots
                        .first()
                        .value
                        .single()
                assertEquals(NORDSTRAND, followed.spot.id)
                assertEquals(60.minutes, followed.drive)
            }
        }

    @Test
    fun `an invitation link opened by a new rider waits through onboarding and then opens the join dialog`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }
            waitFor("welcome")

            assertTrue(graph.koin.get<PendingLinks>().open("pursuitofwind://invite/WIND-4KQ7"))
            // Nothing moves: a rider without a profile has no crew tab to open.
            onNodeWithTag("welcome").assertIsDisplayed()
            onNodeWithTag("welcome.start").performScrollTo().performClick()
            waitFor("profile.form")
            onNodeWithTag("profile.name").performTextInput("Mira")
            onNodeWithTag("profile.weight").performTextInput("70")
            onNodeWithTag("profile.save").performScrollTo().performClick()
            waitFor("spots.done")
            onNodeWithTag("spots.done").performClick()

            waitFor("crew.joinDialog.field")
            onNodeWithTag("crew.joinDialog.field").assert(hasText("WIND-4KQ7"))
            onNodeWithTag("tab.crew").assertIsSelected()
        }

    @Test
    fun `an invitation that waits through onboarding comes back with the screens after the app's state is restored`() =
        runComposeUiTest {
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
            waitFor("welcome")
            val links = graph.koin.get<PendingLinks>()
            assertTrue(links.open("pursuitofwind://invite/WIND-4KQ7"))
            onNodeWithTag("welcome.start").performScrollTo().performClick()
            waitFor("profile.form")

            // The system ends the process while the rider is away: what was in memory is gone, the saved state is not.
            val saved = runOnIdle { registry.performSave() }
            shown = false
            waitForIdle()
            runOnIdle { viewModels.clear() }
            links.opened(AppLink.Invite("WIND-4KQ7"))
            registry = SaveableStateRegistry(restoredValues = saved) { true }
            shown = true

            waitFor("profile.form")
            waitUntil(timeoutMillis = TIMEOUT_MS) { links.link.value == AppLink.Invite("WIND-4KQ7") }
        }

    @Test
    fun `back from the spots returns to the profile with what was saved`() =
        runComposeUiTest {
            setContent {
                KoinIsolatedContext(graph) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { PursuitOfWindTheme { AppNavigation() } }
                }
            }

            waitFor("welcome")
            onNodeWithTag("welcome.start").performScrollTo().performClick()
            waitFor("profile.form")
            onNodeWithTag("profile.name").performTextInput("Mira")
            onNodeWithTag("profile.weight").performTextInput("70")
            onNodeWithTag("profile.save").performScrollTo().performClick()
            waitFor("spots.done")

            onNodeWithContentDescription("Back").performClick()
            waitFor("profile.form")
            onNodeWithTag("profile.name").assert(hasText("Mira"))
        }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        const val TIMEOUT_MS = 10_000L
    }
}
