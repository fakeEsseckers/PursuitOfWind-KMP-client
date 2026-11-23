package io.github.esseckers.pursuitofwind.core.link

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Links into the app are read strictly: the two shapes the app writes itself, and nothing else. */
class AppLinkTest {
    @Test
    fun `an invitation link carries the invite code`() {
        assertEquals(AppLink.Invite("WIND-4KQ7"), AppLink.parse("pursuitofwind://invite/WIND-4KQ7"))
        assertEquals(AppLink.Invite("wind-4kq7"), AppLink.parse("PursuitOfWind://invite/wind-4kq7"))
    }

    @Test
    fun `a spot link carries the spot and the day when it has one`() {
        assertEquals(AppLink.Spot("nordstrand-bay"), AppLink.parse("pursuitofwind://spot/nordstrand-bay"))
        assertEquals(
            AppLink.Spot("spt_0007", LocalDate(2026, 10, 24)),
            AppLink.parse("pursuitofwind://spot/spt_0007?date=2026-10-24"),
        )
    }

    @Test
    fun `every link the app writes reads back as itself`() {
        val links = listOf(AppLink.Invite("WIND-4KQ7"), AppLink.Spot("salt-lagoon"), AppLink.Spot("salt-lagoon", LocalDate(2026, 2, 28)))

        assertEquals(links, links.map { AppLink.parse(it.uri) })
        assertEquals("pursuitofwind://spot/salt-lagoon?date=2026-02-28", links.last().uri)
    }

    @Test
    fun `anything else is no link`() {
        val notLinks =
            listOf(
                "",
                "pursuitofwind://",
                "pursuitofwind://invite",
                "pursuitofwind://invite/",
                "pursuitofwind://demo/saturday-on",
                "https://invite/WIND-4KQ7",
                "pursuitofwind:invite/WIND-4KQ7",
                "pursuitofwind://invite/WIND-4KQ7/extra",
                "pursuitofwind://invite/WIND-4KQ7?date=2026-10-24",
                "pursuitofwind://invite/WIND 4KQ7",
                "pursuitofwind://invite/WIND%2D4KQ7",
                "pursuitofwind://invite/" + "A".repeat(21),
                "pursuitofwind://Invite/WIND-4KQ7",
                "pursuitofwind://spot/",
                "pursuitofwind://spot/../etc",
                "pursuitofwind://spot/nordstrand bay",
                "pursuitofwind://spot/nordstrand-bay#top",
                "pursuitofwind://spot/" + "a".repeat(65),
                "pursuitofwind://spot/nordstrand-bay?",
                "pursuitofwind://spot/nordstrand-bay?date=",
                "pursuitofwind://spot/nordstrand-bay?date=tomorrow",
                "pursuitofwind://spot/nordstrand-bay?date=2026-13-40",
                "pursuitofwind://spot/nordstrand-bay?date=2026-10-24&x=1",
                "pursuitofwind://spot/nordstrand-bay?day=2026-10-24",
                "intent://spot/nordstrand-bay#Intent;scheme=pursuitofwind;end",
            )

        notLinks.forEach { assertNull(AppLink.parse(it), it) }
    }
}
