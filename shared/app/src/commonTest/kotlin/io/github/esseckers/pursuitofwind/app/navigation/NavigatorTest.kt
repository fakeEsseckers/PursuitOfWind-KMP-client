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
    fun `a spot opened from the Spots list takes the place of the one opened from it before`() {
        val nordstrand = Destination.SpotDetail("nordstrand-bay", besideList = true)
        val lakeWeiss = Destination.SpotDetail("lake-weiss", besideList = true)
        val stack = mutableListOf<NavKey>(Destination.Today, Destination.Spots)
        val navigator = Navigator(stack)

        navigator.openBesideList(nordstrand)
        navigator.openBesideList(nordstrand)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots, nordstrand), stack)
        navigator.openBesideList(lakeWeiss)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots, lakeWeiss), stack)
        navigator.back()
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots), stack)
    }

    @Test
    fun `a second tap on a screen's back button while it leaves keeps the screen below`() {
        val stack = mutableListOf<NavKey>(Destination.Today, Destination.Spots, saturday)
        val navigator = Navigator(stack)

        navigator.back(from = saturday)
        navigator.back(from = saturday)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots), stack)
    }

    @Test
    fun `the Profile tab sits on top of Today so back returns to Today`() {
        val stack = mutableListOf<NavKey>(Destination.Today)
        val navigator = Navigator(stack)

        navigator.selectTab(AppTab.PROFILE)
        navigator.selectTab(AppTab.PROFILE)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Profile), stack)
        navigator.back()
        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `choosing a tab closes the screens opened on top of the other one`() {
        val stack = mutableListOf<NavKey>(Destination.Today, saturday)
        val navigator = Navigator(stack)

        navigator.selectTab(AppTab.PROFILE)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Profile), stack)
        navigator.selectTab(AppTab.TODAY)
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
    fun `the spot picker returns to where it was opened when done`() {
        val fromToday = mutableListOf<NavKey>(Destination.Today)
        val fromSpots = mutableListOf<NavKey>(Destination.Today, Destination.Spots)

        Navigator(fromToday).apply {
            open(Destination.PickSpots)
            finishPicking()
        }
        Navigator(fromSpots).apply {
            open(Destination.PickSpots)
            finishPicking()
        }

        assertEquals(listOf<NavKey>(Destination.Today), fromToday)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots), fromSpots)
    }

    @Test
    fun `the spot picker at the end of onboarding goes on to Today`() {
        val stack = mutableListOf<NavKey>(Destination.Welcome, Destination.SetUpProfile, Destination.PickSpots)

        Navigator(stack).finishPicking()

        assertEquals(listOf<NavKey>(Destination.Today), stack)
    }

    @Test
    fun `a second Done after the picker closed leaves the screen below open`() {
        val stack = mutableListOf<NavKey>(Destination.Today, Destination.Spots)

        Navigator(stack).apply {
            open(Destination.PickSpots)
            finishPicking()
            finishPicking()
        }

        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots), stack)
    }

    @Test
    fun `the Spots and Crew tabs sit on top of Today like every tab`() {
        val stack = mutableListOf<NavKey>(Destination.Today, Destination.Profile)
        val navigator = Navigator(stack)

        navigator.selectTab(AppTab.SPOTS)
        val spots = stack.toList()
        navigator.selectTab(AppTab.CREW)

        assertEquals(listOf<NavKey>(Destination.Today, Destination.Spots), spots)
        assertEquals(listOf<NavKey>(Destination.Today, Destination.Crew), stack)
        assertEquals(
            listOf(Destination.Today, Destination.Spots, Destination.Crew, Destination.Profile),
            AppTab.entries.map { it.destination },
        )
    }

    @Test
    fun `a rider without a profile starts at Welcome and everyone else at Today`() {
        val profile = Profile("Alex", RiderProfile(80.0, SkillLevel.INTERMEDIATE, listOf(9.0)), WindUnit.KNOTS)

        assertEquals(Destination.Welcome, startDestination(null))
        assertEquals(Destination.Today, startDestination(profile))
    }
}
