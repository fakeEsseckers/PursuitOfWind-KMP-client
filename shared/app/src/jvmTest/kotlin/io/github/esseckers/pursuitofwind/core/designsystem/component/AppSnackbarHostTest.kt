package io.github.esseckers.pursuitofwind.core.designsystem.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The app's snackbars on the desktop runtime, on the test's clock. */
@OptIn(ExperimentalTestApi::class)
class AppSnackbarHostTest {
    private var result: SnackbarResult? = null

    /** Shows one snackbar with [actionLabel], if any, and keeps how it ended in [result]. */
    private fun ComposeUiTest.show(actionLabel: String?) {
        mainClock.autoAdvance = false
        val hostState = SnackbarHostState()
        setContent {
            PursuitOfWindTheme(darkTheme = false) { AppSnackbarHost(hostState) }
            LaunchedEffect(Unit) { result = hostState.showSnackbar("Salt Lagoon removed", actionLabel = actionLabel) }
        }
        mainClock.advanceTimeBy(FRAME_MILLIS)
        onNodeWithText("Salt Lagoon removed").assertExists()
    }

    /** Still shown just before [SNACKBAR_TIME], dismissed just after it. */
    private fun ComposeUiTest.assertGoesAwayInTime() {
        mainClock.advanceTimeBy(SNACKBAR_TIME.inWholeMilliseconds - MARGIN_MILLIS)
        assertNull(result)
        mainClock.advanceTimeBy(2 * MARGIN_MILLIS)
        waitForIdle()
        assertEquals(SnackbarResult.Dismissed, result)
    }

    @Test
    fun `a snackbar with an action goes away after three seconds`() =
        runComposeUiTest {
            show(actionLabel = "Undo")
            onNodeWithText("Undo").assertExists()

            assertGoesAwayInTime()
        }

    @Test
    fun `a snackbar without an action goes away after three seconds too`() =
        runComposeUiTest {
            show(actionLabel = null)

            assertGoesAwayInTime()
        }

    private companion object {
        const val FRAME_MILLIS = 100L
        const val MARGIN_MILLIS = 500L
    }
}
