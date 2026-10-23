package io.github.esseckers.pursuitofwind.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorTest {
    private val saturday = Destination.SpotDetail("nordstrand-bay", LocalDate(2026, 10, 24))

    @Test
    fun `opening a screen puts it on top and back returns`() {
        val stack = mutableListOf<NavKey>(Destination.Today)
        val navigator = Navigator(stack)

        navigator.open(saturday)
        assertEquals(listOf<NavKey>(Destination.Today, saturday), stack)
        navigator.back()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `a double tap opens one screen and the first screen is never left`() {
        val stack = mutableListOf<NavKey>(Destination.Today)
        val navigator = Navigator(stack)

        navigator.open(saturday)
        navigator.open(saturday)
        navigator.back()
        navigator.back()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }
}
