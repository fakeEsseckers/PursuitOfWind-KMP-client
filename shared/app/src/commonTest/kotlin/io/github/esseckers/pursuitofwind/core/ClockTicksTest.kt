package io.github.esseckers.pursuitofwind.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClockTicksTest {
    /** A clock that reads the test's virtual time. */
    private fun TestScope.virtualClock(): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }

    @Test
    fun `the clock's time comes at once and then every period`() =
        runTest {
            val ticks = virtualClock().ticks(1.minutes).take(3).toList()

            assertEquals(listOf(0L, 60_000L, 120_000L), ticks.map { it.toEpochMilliseconds() })
        }

    @Test
    fun `a clock that does not move ticks once`() =
        runTest {
            val stopped =
                object : Clock {
                    override fun now(): Instant = Instant.fromEpochSeconds(0)
                }
            val ticks = mutableListOf<Instant>()

            val collecting = launch { stopped.ticks(1.minutes).collect { ticks += it } }
            runCurrent()
            advanceTimeBy(10.minutes)
            collecting.cancel()

            assertEquals(listOf(Instant.fromEpochSeconds(0)), ticks)
        }
}
