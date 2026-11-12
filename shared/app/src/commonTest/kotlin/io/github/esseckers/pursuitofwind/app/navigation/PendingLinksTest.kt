package io.github.esseckers.pursuitofwind.app.navigation

import io.github.esseckers.pursuitofwind.core.link.AppLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingLinksTest {
    private val invite = AppLink.Invite("WIND-4KQ7")
    private val spot = AppLink.Spot("nordstrand-bay")

    @Test
    fun `a link the app knows waits until it is opened`() {
        val links = PendingLinks()

        assertTrue(links.open(invite.uri))
        assertEquals(invite, links.link.value)
        links.opened(invite)
        assertNull(links.link.value)
    }

    @Test
    fun `anything else is refused and leaves the waiting link alone`() {
        val links = PendingLinks()
        links.open(invite.uri)

        assertFalse(links.open("pursuitofwind://demo/saturday-on"))
        assertFalse(links.open("https://example.com/invite/WIND-4KQ7"))
        assertEquals(invite, links.link.value)
    }

    @Test
    fun `a newer link takes the place of the one that waits and is not cleared with it`() {
        val links = PendingLinks()
        links.open(invite.uri)
        links.open(spot.uri)

        // The invitation was on its way to the Crew tab when the spot link came in.
        links.opened(invite)

        assertEquals(spot, links.link.value)
    }
}
