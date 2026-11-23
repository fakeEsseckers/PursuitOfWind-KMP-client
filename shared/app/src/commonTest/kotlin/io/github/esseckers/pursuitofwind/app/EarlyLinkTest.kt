package io.github.esseckers.pursuitofwind.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A link handed over before the app's graph exists is kept once, and only when the app knows it. */
class EarlyLinkTest {
    @Test
    fun `a link the app knows is kept and handed on once`() {
        assertTrue(EarlyLink.keep("pursuitofwind://invite/WIND-4KQ7"))

        assertEquals("pursuitofwind://invite/WIND-4KQ7", EarlyLink.take())
        assertNull(EarlyLink.take())
    }

    @Test
    fun `anything else is not kept`() {
        assertFalse(EarlyLink.keep("https://example.com/invite/WIND-4KQ7"))

        assertNull(EarlyLink.take())
    }
}
