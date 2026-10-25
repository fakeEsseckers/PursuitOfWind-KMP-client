package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewEventDto
import io.github.esseckers.pursuitofwind.contract.MemberJoinedEventDto
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.PersonDto
import io.github.esseckers.pursuitofwind.contract.PushTokenRequestDto
import io.github.esseckers.pursuitofwind.contract.RideCreatedEventDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import io.github.esseckers.pursuitofwind.data.fake.Seed
import io.github.esseckers.pursuitofwind.data.fake.SeedRide
import io.github.esseckers.pursuitofwind.data.fake.SeedSpot
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class User(
    val id: String,
    val fallbackName: String,
)

internal class Member(
    val userId: String,
    val role: MemberRoleDto,
    val joinedAt: Instant,
)

internal class Crew(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdAt: Instant,
    val members: MutableList<Member> = mutableListOf(),
    val events: MutableList<CrewEventDto> = mutableListOf(),
)

/** A ride; [plan] is what the driver posted (spot, window, departure, meeting point, seats) and never changes. */
internal class Ride(
    val id: String,
    val crewId: String,
    val driverId: String,
    val plan: CreateRideRequestDto,
    val createdAt: Instant,
) {
    val passengerIds: MutableList<String> = mutableListOf()
    var status: RideStatusDto = RideStatusDto.SCHEDULED
    var version: Int = 1
    val seatsFree: Int get() = plan.seatsTotal - passengerIds.size
}

internal data class AccessGrant(
    val userId: String,
    val expiresAt: Instant,
)

internal data class IdempotencyScope(
    val userId: String,
    val method: String,
    val path: String,
    val key: String,
)

internal data class StoredResponse(
    val requestBody: String,
    val response: FakeResponse,
    val storedAt: Instant,
)

/**
 * Everything the fake server knows. Not thread-safe on its own: [io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer]
 * handles one request at a time under a mutex.
 */
internal class FakeState(
    seed: Seed,
) {
    /**
     * The random draws of new invite codes. Draws are split by purpose, each its own stream of the seed, so that more
     * draws of one kind (a sign-in more) never change the values of another (the next invite code).
     */
    val inviteCodes = Random(seed.randomSeed + INVITE_STREAM)
    val spots: Map<String, SeedSpot> = seed.spots.associateBy { it.spot.id }
    val users = mutableMapOf<String, User>()
    val installations = mutableMapOf<String, String>()
    val accessTokens = mutableMapOf<String, AccessGrant>()
    val refreshTokens = mutableMapOf<String, String>()
    val profiles = mutableMapOf<String, RiderProfileDto>()
    val followedSpots = mutableMapOf<String, MutableMap<String, MySpotUpdateDto>>()
    val crews = mutableMapOf<String, Crew>()
    val rides = mutableMapOf<String, Ride>()
    val idempotency = mutableMapOf<IdempotencyScope, StoredResponse>()
    val pushTokens = mutableMapOf<String, PushTokenRequestDto>()

    /** The model run last served per spot: each forecast request is the next one. */
    val forecastRuns = mutableMapOf<String, ModelRun>()

    /** The seeded crew members. They cannot sign in; [CrewLife] acts for them. */
    val seedMemberIds: Set<String> = seed.crews.flatMap { crew -> crew.members.map { it.userId } }.toSet()

    /** The random draws of tokens, a stream of their own (see [inviteCodes]). */
    private val tokens = Random(seed.randomSeed + TOKEN_STREAM)

    /** The last number given out per id prefix: a user more does not move the ids of crews or rides. */
    private val sequences = mutableMapOf<String, Int>()
    private var eventSequence = 0

    init {
        seed.crews.forEach { seedCrew ->
            val crew = Crew(seedCrew.id, seedCrew.name, seedCrew.inviteCode, seedCrew.createdAt)
            crews[crew.id] = crew
            seedCrew.members.forEachIndexed { index, member ->
                users.getOrPut(member.userId) { User(member.userId, member.displayName) }
                addMember(crew, member.userId, member.role, seedCrew.createdAt + index.hours, announce = member.role != MemberRoleDto.OWNER)
            }
        }
        seed.rides.forEach(::addSeedRide)
    }

    fun nextId(prefix: String): String {
        val number = (sequences[prefix] ?: 0) + 1
        sequences[prefix] = number
        return prefix + "_" + number.toString().padStart(ID_DIGITS, '0')
    }

    /** An unguessable-looking but seeded token. */
    fun newToken(prefix: String): String = nextId(prefix) + "_" + tokens.nextLong().toULong().toString(TOKEN_RADIX)

    fun displayName(userId: String): String = profiles[userId]?.displayName ?: users[userId]?.fallbackName ?: userId

    fun person(userId: String): PersonDto = PersonDto(userId, displayName(userId))

    fun crew(id: String): Crew = crews[id] ?: throw notFound("Crew $id")

    fun ride(id: String): Ride = rides[id] ?: throw notFound("Ride $id")

    fun requireMember(
        crew: Crew,
        userId: String,
    ) {
        if (crew.members.none { it.userId == userId }) throw notAMember()
    }

    /** Appends an event to the crew's feed; [build] receives the new event id. */
    fun addEvent(
        crew: Crew,
        build: (id: String) -> CrewEventDto,
    ) {
        crew.events += build("evt_" + (++eventSequence).toString().padStart(EVENT_DIGITS, '0'))
    }

    fun addMember(
        crew: Crew,
        userId: String,
        role: MemberRoleDto,
        joinedAt: Instant,
        announce: Boolean,
    ) {
        val member = Member(userId, role, joinedAt)
        crew.members += member
        if (announce) addEvent(crew) { id -> MemberJoinedEventDto(id, crew.id, joinedAt, person(userId), memberDto(member)) }
    }

    private fun addSeedRide(seedRide: SeedRide) {
        val crew = crew(seedRide.crewId)
        val plan =
            CreateRideRequestDto(
                seedRide.spotId,
                seedRide.windowStart,
                seedRide.windowEnd,
                seedRide.departAt,
                seedRide.meetingPoint,
                seedRide.seatsTotal,
            )
        val ride = Ride(seedRide.id, seedRide.crewId, seedRide.driverId, plan, seedRide.createdAt)
        rides[ride.id] = ride
        addEvent(crew) { id -> RideCreatedEventDto(id, crew.id, ride.createdAt, person(ride.driverId), rideDto(ride)) }
        seedRide.passengerIds.forEachIndexed { index, passengerId ->
            ride.passengerIds += passengerId
            ride.version++
            val at = ride.createdAt + SEED_SEAT_GAP * (index + 1)
            addEvent(crew) { id -> SeatTakenEventDto(id, crew.id, at, person(passengerId), ride.id, ride.version, ride.seatsFree) }
        }
    }

    private companion object {
        const val ID_DIGITS = 4
        const val EVENT_DIGITS = 6
        const val TOKEN_RADIX = 36
        const val INVITE_STREAM = 0x1C0DEL
        const val TOKEN_STREAM = 0x70CE4L
        val SEED_SEAT_GAP = 10.minutes
    }
}
