package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewEventDto
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RideCreatedEventDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideListDto
import io.github.esseckers.pursuitofwind.contract.SeatReleasedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The seeded crew members acting on their own ([MemberActivity]). */
class CrewLifeTest {
    private val seedMembers = setOf("usr_andrei", "usr_mira", "usr_jonas")

    /** A rider who joined the Weekend Crew, and the server's clock. Access tokens last an hour, so [live] signs in again. */
    private class Joined(
        val driver: FakeApiDriver,
        val clock: ControllableClock,
        var token: String,
    )

    private suspend fun TestScope.joined(seed: (ControllableClock) -> Seed = { Seed.default(it.now()) }): Joined {
        val clock = ControllableClock(THURSDAY_MORNING)
        val driver = fakeDriver(clock, seed(clock))
        val token = driver.signIn()
        driver.send(ApiRoute.JOIN_CREW, token = token, value = JoinCrewRequestDto("WIND-4KQ7"))
        return Joined(driver, clock, token)
    }

    /** The Weekend Crew's feed: the newest 100 events, more than any test here makes. */
    private suspend fun Joined.events(): List<CrewEventDto> {
        val feed =
            driver
                .get(ApiRoute.GET_CREW_FEED, "crw_weekend", token = token, query = mapOf("limit" to "100"))
                .decode(CrewFeedDto.serializer())
        assertTrue(feed.items.size < 100, "The feed is longer than this test can read")
        return feed.items
    }

    /** Moves the clock [steps] times by [step], asking for the rides after each move, as a polling app does. */
    private suspend fun Joined.live(
        steps: Int,
        step: Duration = 1.minutes,
    ) {
        repeat(steps) {
            clock.advanceBy(step)
            token = driver.signIn()
            driver.call(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = token)
        }
    }

    private suspend fun Joined.rides(): List<RideDto> =
        driver.call(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = token).decode(RideListDto.serializer()).items

    @Test
    fun `the members act now and then and each action shows in the feed at its time`() =
        runTest {
            val alex = joined()
            val before = alex.events()

            alex.live(steps = 60)
            val after = alex.events()
            val new = after.drop(before.size)

            // A member tries every one to three minutes, 20 to 60 times an hour; a try can find nothing to do.
            assertTrue(new.size in 3..60, "${new.size} actions in an hour")
            assertTrue(new.all { it.actor.userId in seedMembers }, new.toString())
            assertTrue(new.all { it is SeatTakenEventDto || it is SeatReleasedEventDto || it is RideCreatedEventDto }, new.toString())
            assertEquals(new.map { it.occurredAt }.sorted(), new.map { it.occurredAt })
            assertTrue(new.all { it.occurredAt > THURSDAY_MORNING && it.occurredAt <= alex.clock.now() })
        }

    @Test
    fun `the same seed clock and requests give the same crew life`() =
        runTest {
            val quiet = joined().events()
            val first = joined().apply { live(steps = 30) }.events()
            val second = joined().apply { live(steps = 30) }.events()

            assertTrue(first.size > quiet.size, "The members did nothing")
            assertEquals(first, second)
        }

    @Test
    fun `nothing happens while the clock stands still`() =
        runTest {
            val alex = joined()
            val before = alex.events()

            alex.live(steps = 20, step = Duration.ZERO)

            assertEquals(before, alex.events())
        }

    @Test
    fun `after a long pause the members do a few things not a whole day of them`() =
        runTest {
            val alex = joined()
            val before = alex.events()

            alex.live(steps = 1, step = 1.days)
            val new = alex.events().drop(before.size)

            assertTrue(new.size in 1..3)
            // In the last few gaps before now, not at times a day ago.
            assertTrue(new.all { it.occurredAt > alex.clock.now() - 10.minutes }, new.toString())
        }

    @Test
    fun `a still crew stays still`() =
        runTest {
            val alex = joined { Seed.default(it.now()).copy(memberActivity = null) }
            val before = alex.events()

            alex.live(steps = 60)

            assertEquals(before, alex.events())
        }

    @Test
    fun `members offer rides on GO windows with time to plan and keep at most three coming up`() =
        runTest {
            val alex =
                joined { clock ->
                    Seed.default(clock.now()).let { it.copy(spots = it.spots.map { spot -> spot.copy(forecast = STEADY) }) }
                }

            alex.live(steps = 6, step = 1.hours)
            val offered =
                alex
                    .events()
                    .filterIsInstance<RideCreatedEventDto>()
                    .filter { it.actor.userId in seedMembers }
                    .drop(1)
            val rides = alex.rides()

            assertTrue(offered.isNotEmpty())
            offered.forEach { event ->
                val ride = event.ride
                assertTrue(ride.driver.userId in seedMembers)
                assertTrue(ride.departAt >= event.occurredAt + 2.hours, "$ride offered at ${event.occurredAt}")
                assertTrue(ride.departAt <= ride.windowStart && ride.windowStart < ride.windowEnd)
                assertTrue(ride.seatsTotal in 2..4)
            }
            assertTrue(rides.count { it.departAt > alex.clock.now() } <= 3, rides.toString())
        }

    @Test
    fun `members only take seats that are free`() =
        runTest {
            val alex =
                joined { clock ->
                    Seed.default(clock.now()).let { it.copy(spots = it.spots.map { spot -> spot.copy(forecast = STEADY) }) }
                }
            val window = alex.clock.now() + 1.days
            val oneSeat = CreateRideRequestDto("nordstrand-bay", window, window + 4.hours, window - 2.hours, "Old harbour", seatsTotal = 1)
            val created =
                alex.driver.send(
                    ApiRoute.CREATE_RIDE,
                    "crw_weekend",
                    token = alex.token,
                    value = oneSeat,
                    idempotencyKey = FakeApiDriver.key(1),
                )
            assertEquals(201, created.status, created.toString())
            val mine = created.decode(RideDto.serializer()).id

            alex.live(steps = 120)
            val rides = alex.rides()
            val seats = alex.events().filterIsInstance<SeatTakenEventDto>()
            val inMine = seats.filter { it.rideId == mine }

            // Members took the rider's one seat, and nobody took a seat in a full car, in his or any other.
            assertTrue(inMine.isNotEmpty(), seats.toString())
            assertTrue(inMine.all { it.seatsFree == 0 }, inMine.toString())
            assertTrue(rides.all { it.seatsFree >= 0 && it.passengers.size <= it.seatsTotal }, rides.toString())
            assertTrue(seats.all { it.seatsFree >= 0 }, seats.toString())
        }

    @Test
    fun `a member sometimes takes the last free seat first and the rider finds the car full`() =
        runTest {
            val outcomes =
                (1L..12L).map { randomSeed ->
                    val alex = joined { clock -> lastSeatLeft(clock).copy(randomSeed = randomSeed) }
                    val answer = alex.driver.call(ApiRoute.TAKE_SEAT, "rid_last", token = alex.token)
                    val ride = alex.rides().single { it.id == "rid_last" }
                    if (answer.status == 409) {
                        assertEquals(ProblemCode.SEATS_FULL, answer.problem().code)
                        assertEquals(listOf("Mira", "Jonas"), ride.passengers.map { it.displayName })
                    } else {
                        assertEquals(201, answer.status)
                        assertEquals("Mira", ride.passengers.first().displayName)
                    }
                    answer.status
                }

            assertEquals(setOf(201, 409), outcomes.toSet())
        }

    @Test
    fun `there is no race for the last seat when it is turned off or nobody else wants it`() =
        runTest {
            val off = joined { lastSeatLeft(it).let { seed -> seed.copy(memberActivity = seed.memberActivity!!.copy(lastSeatRace = 0.0)) } }
            val always =
                joined {
                    lastSeatLeft(it).let { seed ->
                        seed.copy(memberActivity = seed.memberActivity!!.copy(lastSeatRace = 1.0))
                    }
                }
            // Jonas, the only one who could want the seat, already drives at that time.
            val busy =
                joined { clock ->
                    val seed = lastSeatLeft(clock)
                    val last = seed.rides.single()
                    val jonasDrives = last.copy(id = "rid_jonas", driverId = "usr_jonas", passengerIds = emptyList(), seatsTotal = 1)
                    seed.copy(rides = seed.rides + jonasDrives, memberActivity = seed.memberActivity!!.copy(lastSeatRace = 1.0))
                }

            assertEquals(201, off.driver.call(ApiRoute.TAKE_SEAT, "rid_last", token = off.token).status)
            assertEquals(409, always.driver.call(ApiRoute.TAKE_SEAT, "rid_last", token = always.token).status)
            assertEquals(201, busy.driver.call(ApiRoute.TAKE_SEAT, "rid_last", token = busy.token).status)
        }

    private companion object {
        /** 18 kn from 240° all the time: GO for a typical member at every spot that takes that direction. */
        val STEADY =
            ForecastProfile(
                meanKn = 18.0,
                swingKn = 0.0,
                swingPeriodHours = 24,
                seaBreezeKn = 0.0,
                bearingDeg = 240.0,
                bearingSwingDeg = 0.0,
                gustFactor = 0.1,
                modelSpreadKn = 1.0,
                noiseKn = 0.0,
                rainyShare = 0.0,
                temperatureC = 18.0,
            )

        /** The Weekend Crew with Andrei's car one seat from full: Mira is in, Jonas could still want the last seat. */
        fun lastSeatLeft(clock: ControllableClock): Seed {
            val seed = Seed.default(clock.now())
            val ride = seed.rides.single().copy(id = "rid_last", seatsTotal = 2)
            return seed.copy(rides = listOf(ride))
        }
    }
}
