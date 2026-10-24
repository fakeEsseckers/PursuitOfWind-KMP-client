package io.github.esseckers.pursuitofwind.data.fake

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How the seeded crew members (the Weekend Crew's Andrei, Mira and Jonas) act on their own, so a crew feels alive the
 * way a real one does. Time is the server's clock, so tests move it with [ControllableClock].
 *
 * @property minGap shortest time between two actions of the members.
 * @property maxGap longest time between two actions; each gap is seeded somewhere in between.
 * @property lastSeatRace the share of a rider's tries for the last free seat of a ride that a member wins first, so the
 *   rider gets `409 SEATS_FULL`. Zero turns the race off.
 */
public data class MemberActivity(
    val minGap: Duration = 1.minutes,
    val maxGap: Duration = 3.minutes,
    val lastSeatRace: Double = 0.5,
) {
    init {
        require(minGap.isPositive() && maxGap >= minGap) { "Gaps must be positive and minGap <= maxGap, were $minGap and $maxGap" }
        require(lastSeatRace in 0.0..1.0) { "The last-seat race is a share, was $lastSeatRace" }
    }
}
