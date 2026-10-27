package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.data.fake.MemberActivity
import io.github.esseckers.pursuitofwind.data.internal.mapping.toForecastHour
import io.github.esseckers.pursuitofwind.data.internal.mapping.toSpot
import io.github.esseckers.pursuitofwind.data.internal.startOfHour
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The seeded crew members acting on their own, as people in a real crew do. Every [MemberActivity.minGap] to
 * [MemberActivity.maxGap] of clock time one of them does one thing:
 *
 * - takes a free seat in a ride of their crew (most often), unless they already ride or drive at that time;
 * - offers a ride to a spot where the decision engine finds a GO window for them in the next two days, while the crew
 *   has fewer than [MAX_UPCOMING_RIDES] rides coming up;
 * - now and then leaves a ride, and does not come back to it.
 *
 * The server calls [catchUp] before every request, so each answer shows what happened until then, stamped with the
 * time it happened. After a long pause (the app was closed for a day) the members do a few things in the last minutes,
 * not a day's worth.
 * Everything is seeded: the same seed, clock and requests give the same crew life.
 */
internal class CrewLife(
    private val state: FakeState,
    private val activity: MemberActivity?,
    private val seed: Long,
) {
    private val random = Random(seed + RANDOM_SALT)

    /** Its own stream, drawn twice per contested seat whoever wants it, so a race never shifts the members' schedule. */
    private val seatRaces = Random(seed + SEAT_RACE_SALT)

    /** When the members act next; set by the first request. */
    private var nextAt: Instant? = null

    /** Ride id and member of every seat a member gave up; they changed their mind and stay out of that ride. */
    private val gaveUp = mutableSetOf<Pair<String, String>>()

    fun catchUp(now: Instant) {
        val activity = activity ?: return
        // After a long pause the members act in the last few gaps before now, not at long-gone times.
        var at = maxOf(nextAt ?: (now + gap(activity)), now - activity.maxGap * MAX_CATCH_UP)
        var acted = 0
        while (at <= now && acted < MAX_CATCH_UP) {
            act(at)
            acted++
            at += gap(activity)
        }
        nextAt = if (at <= now) now + gap(activity) else at
    }

    /**
     * Before [userId] takes the last free seat of [ride], a member who wants a seat may get it first, as happens in a
     * real crew, in [MemberActivity.lastSeatRace] of the tries. The rider's request then finds the car full.
     */
    fun contestLastSeat(
        ride: Ride,
        crew: Crew,
        userId: String,
        now: Instant,
    ) {
        val share = activity?.lastSeatRace ?: 0.0
        val contested =
            share > 0.0 &&
                ride.status == RideStatusDto.SCHEDULED &&
                ride.seatsFree == 1 &&
                userId !in state.seedMemberIds &&
                userId != ride.driverId &&
                userId !in ride.passengerIds
        if (!contested) return
        val seekers = seatSeekers(ride, crew, now)
        val pick = seatRaces.nextInt(Int.MAX_VALUE)
        val wins = seatRaces.nextDouble() < share
        if (seekers.isNotEmpty() && wins) state.takeSeat(ride, crew, seekers[pick % seekers.size], now)
    }

    private fun act(at: Instant) {
        val upcoming =
            state.rides.values
                .filter { it.status == RideStatusDto.SCHEDULED && it.plan.departAt > at }
                .sortedBy { it.id }
        val takes =
            upcoming
                .filter { it.seatsFree > 0 }
                .flatMap { ride -> seatSeekers(ride, state.crew(ride.crewId), at).map { ride to it } }
        val leaves = upcoming.flatMap { ride -> ride.passengerIds.filter { it in state.seedMemberIds }.map { ride to it } }
        val crews =
            state.crews.values
                .filter { crew -> crew.members.any { it.userId in state.seedMemberIds } }
                .filter { crew -> upcoming.count { it.crewId == crew.id } < MAX_UPCOMING_RIDES }
                .sortedBy { it.id }
        val roll = random.nextInt(LEAVE_WEIGHT + OFFER_WEIGHT + TAKE_WEIGHT)
        val offer = { crews.isNotEmpty() && offerRide(crews.random(random), at) }
        val take = { seatChange(takes, at, take = true) }
        // Taking a seat and offering a ride stand in for each other when one cannot happen now. Leaving does not: a crew
        // with every seat taken would empty its cars.
        when {
            roll < LEAVE_WEIGHT -> seatChange(leaves, at, take = false)
            roll < LEAVE_WEIGHT + OFFER_WEIGHT -> if (!offer()) take()
            else -> if (!take()) offer()
        }
    }

    /** One of [candidates] (a ride and a member) takes or leaves a seat; false when there is none. */
    private fun seatChange(
        candidates: List<Pair<Ride, String>>,
        at: Instant,
        take: Boolean,
    ): Boolean {
        val pick = candidates.randomOrNull(random)
        if (pick != null) {
            val (ride, member) = pick
            if (take) {
                state.takeSeat(ride, state.crew(ride.crewId), member, at)
            } else {
                state.releaseSeat(ride, state.crew(ride.crewId), member, at)
                gaveUp += ride.id to member
            }
        }
        return pick != null
    }

    /** Seeded members of [crew] who could take a seat in [ride]: not in it, never left it, and free at that time. */
    private fun seatSeekers(
        ride: Ride,
        crew: Crew,
        at: Instant,
    ): List<String> =
        crew.members
            .map { it.userId }
            .filter { it in state.seedMemberIds && it != ride.driverId && it !in ride.passengerIds && (ride.id to it) !in gaveUp }
            .filterNot { member -> busy(member, ride.plan.windowStart, ride.plan.windowEnd, at) }

    /** True when [member] drives or rides in a scheduled ride, still to come at [at], whose window overlaps the one given. */
    private fun busy(
        member: String,
        start: Instant,
        end: Instant,
        at: Instant,
    ): Boolean =
        state.rides.values.any { other ->
            other.status == RideStatusDto.SCHEDULED &&
                other.plan.departAt > at &&
                (other.driverId == member || member in other.passengerIds) &&
                other.plan.windowStart < end &&
                start < other.plan.windowEnd
        }

    /** A member of [crew] offers a ride on a GO window; false when no member is free or no window suits. */
    private fun offerRide(
        crew: Crew,
        at: Instant,
    ): Boolean {
        val drivers = crew.members.map { it.userId }.filter { it in state.seedMemberIds }
        val choices =
            if (drivers.isEmpty()) {
                emptyList()
            } else {
                goodWindows(at).flatMap { window ->
                    val clash =
                        state.rides.values.any {
                            it.crewId == crew.id && it.status == RideStatusDto.SCHEDULED &&
                                overlaps(it, window)
                        }
                    drivers.filterNot { busy(it, window.start, window.end, at) || clash }.map { it to window }
                }
            }
        val choice = choices.randomOrNull(random)
        if (choice != null) {
            val (driver, window) = choice
            val plan =
                CreateRideRequestDto(
                    spotId = window.spotId.value,
                    windowStart = window.start,
                    windowEnd = window.end,
                    departAt = window.leaveBy,
                    meetingPoint = MEETING_POINTS.random(random),
                    seatsTotal = random.nextInt(MIN_SEATS, MAX_SEATS + 1),
                )
            state.offerRide(crew, driver, plan, at)
        }
        return choice != null
    }

    /**
     * GO windows of the next two days that leave time to plan, as the engine finds them for a typical member, at the
     * seeded spots only: a spot a rider adds may be anywhere in the world, far from the members' meeting points.
     */
    private fun goodWindows(at: Instant): List<SessionWindow> {
        val from = at.startOfHour()
        val seeded = state.spots.values.filter { it.spot.id in state.seededSpotIds }
        return seeded.sortedBy { it.spot.id }.flatMap { seedSpot ->
            val config = MySpot(seedSpot.spot.toSpot(), MEMBER_DRIVE, favorite = true).toSpotConfig()
            // The run riders were last served, so members judge the forecast riders see.
            val run = state.forecastRuns[seedSpot.spot.id] ?: ModelRun(number = 0, at = at)
            val verdicts =
                ForecastGenerator
                    .hours(seedSpot, from, LOOK_AHEAD_HOURS, seed, run)
                    .map { DecisionEngine.evaluateHour(it.toForecastHour(), MEMBER, config) }
            DecisionEngine.findWindows(verdicts, config, MEMBER, at).filter { it.qualifies && it.leaveBy >= at + PLANNING_TIME }
        }
    }

    private fun overlaps(
        ride: Ride,
        window: SessionWindow,
    ): Boolean = ride.plan.spotId == window.spotId.value && ride.plan.windowStart < window.end && window.start < ride.plan.windowEnd

    private fun gap(activity: MemberActivity): Duration {
        val spread = (activity.maxGap - activity.minGap).inWholeSeconds
        return activity.minGap + (if (spread > 0) random.nextLong(spread + 1) else 0L).seconds
    }

    private companion object {
        const val RANDOM_SALT = 0x5EA7L
        const val SEAT_RACE_SALT = 0x5EA70L
        const val MAX_CATCH_UP = 3
        const val MAX_UPCOMING_RIDES = 3
        const val LEAVE_WEIGHT = 1
        const val OFFER_WEIGHT = 3
        const val TAKE_WEIGHT = 6
        const val MIN_SEATS = 2
        const val MAX_SEATS = 4
        const val LOOK_AHEAD_HOURS = 48
        val MEMBER_DRIVE = 60.minutes
        val PLANNING_TIME = 2.hours

        /** A typical member: the rider the engine decides for when a member picks a window. */
        val MEMBER = RiderProfile(weightKg = 78.0, level = SkillLevel.INTERMEDIATE, quiverM2 = listOf(7.0, 9.0, 12.0))

        val MEETING_POINTS = listOf("Hauptbahnhof, north exit", "Park & Ride Nord", "Bakery on Hafenstraße", "Old harbour car park")
    }
}
