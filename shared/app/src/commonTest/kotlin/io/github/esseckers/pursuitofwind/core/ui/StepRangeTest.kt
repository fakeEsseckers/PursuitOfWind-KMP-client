package io.github.esseckers.pursuitofwind.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StepRangeTest {
    private val range = StepRange(min = 30, max = 720, step = 30)

    @Test
    fun `a value moves one step either way`() {
        assertEquals(90, range.next(60, Step.UP))
        assertEquals(30, range.next(60, Step.DOWN))
    }

    @Test
    fun `a step stops at the end of the range`() {
        assertEquals(720, range.next(705, Step.UP))
        assertEquals(720, range.next(720, Step.UP))
        assertEquals(30, range.next(45, Step.DOWN))
        assertEquals(30, range.next(30, Step.DOWN))
    }

    @Test
    fun `a value outside the range only steps back towards it`() {
        assertEquals(900, range.next(900, Step.UP))
        assertEquals(870, range.next(900, Step.DOWN))
        assertEquals(15, range.next(15, Step.DOWN))
        assertEquals(45, range.next(15, Step.UP))
    }

    @Test
    fun `the stepper offers only the ways the range goes on`() {
        assertEquals(StepperUi(30, canDecrease = false, canIncrease = true), range.stepper(30))
        assertEquals(StepperUi(60, canDecrease = true, canIncrease = true), range.stepper(60))
        assertEquals(StepperUi(720, canDecrease = true, canIncrease = false), range.stepper(720))
        assertEquals(StepperUi(900, canDecrease = true, canIncrease = false), range.stepper(900))
        assertEquals(StepperUi(15, canDecrease = false, canIncrease = true), range.stepper(15))
    }
}
