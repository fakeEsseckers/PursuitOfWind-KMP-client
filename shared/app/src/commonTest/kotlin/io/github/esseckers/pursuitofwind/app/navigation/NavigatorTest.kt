package io.github.esseckers.pursuitofwind.app.navigation

import androidx.navigation3.runtime.NavKey
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
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

    @Test
    fun `the Profile tab sits on top of Today so back returns to Today`() {
        val stack = mutableListOf<NavKey>(Destination.Today)
        val navigator = Navigator(stack)

        navigator.selectTab(Destination.Profile)
        navigator.selectTab(Destination.Profile)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Profile), stack)
        navigator.back()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `choosing a tab closes the screens opened on top of the other one`() {
        val stack = mutableListOf<NavKey>(Destination.Today, saturday)
        val navigator = Navigator(stack)

        navigator.selectTab(Destination.Profile)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Profile), stack)
        navigator.selectTab(Destination.Today)
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `finishing onboarding leaves Today as the only screen`() {
        val stack = mutableListOf<NavKey>(Destination.Welcome)
        val navigator = Navigator(stack)

        navigator.open(Destination.SetUpProfile)
        navigator.open(Destination.PickSpots)
        navigator.finishSetup()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
        navigator.back()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `the spot picker opened from Today returns to Today when done`() {
        val stack = mutableListOf<NavKey>(Destination.Today)
        val navigator = Navigator(stack)

        navigator.open(Destination.PickSpots)
        navigator.finishSetup()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `a rider without a profile starts at Welcome and everyone else at Today`() {
        val profile = Profile("Alex", RiderProfile(80.0, SkillLevel.INTERMEDIATE, listOf(9.0)), WindUnit.KNOTS)

        assertEquals(Destination.Welcome, startDestination(null))
        assertEquals(Destination.Today, startDestination(profile))
    }
}
