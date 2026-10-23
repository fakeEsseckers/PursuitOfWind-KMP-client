package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
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

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        const val TIMEOUT_MS = 10_000L
    }
}
