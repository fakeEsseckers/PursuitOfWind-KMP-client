package io.github.esseckers.pursuitofwind.contract

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiLimitsTest {
    private val kite = "🪁"

    @Test
    fun `text is as long as the API counts it with an emoji as one character`() {
        assertEquals(0, ApiLimits.lengthOf(""))
        assertEquals(5, ApiLimits.lengthOf("Dunes"))
        assertEquals(6, ApiLimits.lengthOf("Dunes$kite"))
    }

    @Test
    fun `text is cut to whole characters`() {
        assertEquals("Dun", ApiLimits.cut("Dunes", 3))
        assertEquals("Dunes", ApiLimits.cut("Dunes", 10))
        assertEquals("Du$kite", ApiLimits.cut("Du${kite}nes", 3))
        assertEquals("Du", ApiLimits.cut("Du$kite", 2))
        assertEquals("$kite$kite", ApiLimits.cut("$kite$kite$kite", 2))
    }

    @Test
    fun `a stray half of an emoji counts and is cut as a character of its own`() {
        val high = kite.first().toString()
        val low = kite.last().toString()

        assertEquals(2, ApiLimits.lengthOf("a$high"))
        assertEquals(2, ApiLimits.lengthOf("a$low"))
        assertEquals(3, ApiLimits.lengthOf("$low$high$kite"))
        assertEquals("a$high", ApiLimits.cut("a${high}b", 2))
    }
}
