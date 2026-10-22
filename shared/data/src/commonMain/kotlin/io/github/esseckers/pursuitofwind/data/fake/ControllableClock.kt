package io.github.esseckers.pursuitofwind.data.fake

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A clock that only moves when told to. The fake server, and in demo mode the whole app, read time from it, so
 * "two hours later" is one call instead of a wait.
 *
 * Reads are safe from any thread. Move it from one place at a time (a test, the demo panel): two concurrent
 * [advanceBy] calls can lose one of the steps.
 */
public class ControllableClock(
    start: Instant,
) : Clock {
    @Volatile
    private var current: Instant = start

    override fun now(): Instant = current

    public fun advanceBy(duration: Duration) {
        require(!duration.isNegative()) { "Time only moves forward, got $duration" }
        current += duration
    }

    /** Jumps to [instant], also backwards (for example when a demo restarts). */
    public fun set(instant: Instant) {
        current = instant
    }
}
