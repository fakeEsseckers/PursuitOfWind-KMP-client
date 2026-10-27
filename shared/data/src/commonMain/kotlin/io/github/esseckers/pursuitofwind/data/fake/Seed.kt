package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.DataQualityDto
import io.github.esseckers.pursuitofwind.contract.GeoPointDto
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.SectorDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Initial state of the fake server. Everything the fake generates later (ids, invite codes, flaky failures, what the
 * crew members do) comes from [randomSeed], so the same seed, clock and requests always give the same responses.
 *
 * A seed is checked when it is built, so a broken seed fails at once instead of serving invalid responses.
 *
 * @property memberActivity how the members of [crews] act on their own; null keeps them still.
 */
public data class Seed(
    val spots: List<SeedSpot>,
    val crews: List<SeedCrew> = emptyList(),
    val rides: List<SeedRide> = emptyList(),
    val randomSeed: Long = 1,
    val memberActivity: MemberActivity? = null,
) {
    init {
        requireUnique("spot id", spots.map { it.spot.id })
        requireUnique("crew id", crews.map { it.id })
        requireUnique("invite code", crews.map { it.inviteCode.uppercase() })
        requireUnique("ride id", rides.map { it.id })
        val spotIds = spots.map { it.spot.id }.toSet()
        val crewsById = crews.associateBy { it.id }
        rides.forEach { ride ->
            require(ride.spotId in spotIds) { "Ride ${ride.id} goes to unknown spot ${ride.spotId}" }
            val crew = requireNotNull(crewsById[ride.crewId]) { "Ride ${ride.id} belongs to unknown crew ${ride.crewId}" }
            val members = crew.members.map { it.userId }.toSet()
            require(ride.driverId in members && members.containsAll(ride.passengerIds)) {
                "Driver and passengers of ride ${ride.id} must be members of ${crew.id}"
            }
        }
    }

    public companion object {
        private const val SECONDS_PER_DAY = 86_400L

        /**
         * Five fictional spots on the German coast and inland, and Andrei's "Weekend Crew" (invite code `WIND-4KQ7`)
         * with one ride two days after [now]. Its members act on their own ([MemberActivity] defaults). A new user
         * starts with no profile and no crew.
         */
        public fun default(now: Instant): Seed {
            val dayAfterTomorrow = Instant.fromEpochSeconds((now.epochSeconds / SECONDS_PER_DAY + 2) * SECONDS_PER_DAY)
            val andrei = SeedMember("usr_andrei", "Andrei", MemberRoleDto.OWNER)
            return Seed(
                spots = DEFAULT_SPOTS,
                crews =
                    listOf(
                        SeedCrew(
                            id = "crw_weekend",
                            name = "Weekend Crew",
                            inviteCode = "WIND-4KQ7",
                            createdAt = now - 30.days,
                            members =
                                listOf(
                                    andrei,
                                    SeedMember("usr_mira", "Mira", MemberRoleDto.MEMBER),
                                    SeedMember("usr_jonas", "Jonas", MemberRoleDto.MEMBER),
                                ),
                        ),
                    ),
                rides =
                    listOf(
                        SeedRide(
                            id = "rid_7f3c",
                            crewId = "crw_weekend",
                            spotId = "nordstrand-bay",
                            driverId = andrei.userId,
                            windowStart = dayAfterTomorrow + 11.hours,
                            windowEnd = dayAfterTomorrow + 15.hours,
                            departAt = dayAfterTomorrow + 8.hours + 30.minutes,
                            meetingPoint = "Hauptbahnhof, north exit",
                            seatsTotal = 3,
                            passengerIds = listOf("usr_mira"),
                            createdAt = now - 1.days,
                        ),
                    ),
                memberActivity = MemberActivity(),
            )
        }
    }
}

public data class SeedSpot(
    val spot: SpotDto,
    val forecast: ForecastProfile,
)

public data class SeedMember(
    val userId: String,
    val displayName: String,
    val role: MemberRoleDto,
)

public data class SeedCrew(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdAt: Instant,
    val members: List<SeedMember>,
) {
    init {
        require(members.count { it.role == MemberRoleDto.OWNER } == 1) { "Crew $id needs exactly one owner" }
        requireUnique("member of crew $id", members.map { it.userId })
    }
}

public data class SeedRide(
    val id: String,
    val crewId: String,
    val spotId: String,
    val driverId: String,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
    val meetingPoint: String,
    val seatsTotal: Int,
    val passengerIds: List<String>,
    val createdAt: Instant,
) {
    init {
        require(seatsTotal in SEATS) { "Ride $id offers $seatsTotal seats, the spec allows ${SEATS.first} to ${SEATS.last}" }
        require(passengerIds.size <= seatsTotal) { "Ride $id has more passengers than seats" }
        requireUnique("passenger of ride $id", passengerIds)
        require(driverId !in passengerIds) { "The driver of ride $id cannot be a passenger" }
        require(windowStart < windowEnd && departAt <= windowStart) { "Ride $id: departAt <= windowStart < windowEnd is required" }
        require(meetingPoint.isNotBlank()) { "Ride $id needs a meeting point" }
    }

    private companion object {
        val SEATS = ApiLimits.Ride.SEATS_MIN..ApiLimits.Ride.SEATS_MAX
    }
}

private fun requireUnique(
    what: String,
    values: List<String>,
) {
    val duplicates =
        values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    require(duplicates.isEmpty()) { "Duplicate $what: $duplicates" }
}

private fun spot(
    id: String,
    name: String,
    location: GeoPointDto,
    rideable: List<SectorDto>,
    danger: List<SectorDto> = emptyList(),
) = SpotDto(id, name, location, "Europe/Berlin", rideable, danger, DataQualityDto.VERIFIED)

/** How much a new model run moves the wind three days out, like a real forecast update: a few knots, a few degrees. */
private const val LIVE_DRIFT_KN = 4.0
private const val LIVE_DRIFT_DEG = 15.0

private val DEFAULT_SPOTS: List<SeedSpot> =
    listOf(
        SeedSpot(
            spot(
                "nordstrand-bay",
                "Nordstrand Bay",
                GeoPointDto(54.4936, 8.8131),
                listOf(SectorDto(180.0, 300.0)),
                listOf(SectorDto(60.0, 150.0)),
            ),
            ForecastProfile(
                meanKn = 15.0,
                swingKn = 6.0,
                swingPeriodHours = 96,
                seaBreezeKn = 3.0,
                bearingDeg = 240.0,
                bearingSwingDeg = 40.0,
                gustFactor = 0.32,
                modelSpreadKn = 2.0,
                noiseKn = 1.5,
                rainyShare = 0.12,
                temperatureC = 17.0,
                runDriftKn = LIVE_DRIFT_KN,
                runDriftDeg = LIVE_DRIFT_DEG,
            ),
        ),
        SeedSpot(
            spot("salt-lagoon", "Salt Lagoon", GeoPointDto(53.0712, 8.2156), listOf(SectorDto(200.0, 340.0))),
            ForecastProfile(
                meanKn = 14.0,
                swingKn = 5.0,
                swingPeriodHours = 110,
                seaBreezeKn = 2.5,
                bearingDeg = 260.0,
                bearingSwingDeg = 35.0,
                gustFactor = 0.3,
                modelSpreadKn = 2.5,
                noiseKn = 1.5,
                rainyShare = 0.15,
                temperatureC = 16.0,
                runDriftKn = LIVE_DRIFT_KN,
                runDriftDeg = LIVE_DRIFT_DEG,
            ),
        ),
        SeedSpot(
            spot(
                "lake-weiss",
                "Lake Weiss",
                GeoPointDto(52.4123, 11.6274),
                listOf(SectorDto(220.0, 320.0)),
            ).copy(dataQuality = DataQualityDto.UNVERIFIED),
            ForecastProfile(
                meanKn = 11.0,
                swingKn = 5.0,
                swingPeriodHours = 96,
                seaBreezeKn = 1.5,
                bearingDeg = 250.0,
                bearingSwingDeg = 50.0,
                gustFactor = 0.4,
                modelSpreadKn = 3.0,
                noiseKn = 2.0,
                rainyShare = 0.18,
                temperatureC = 18.0,
                runDriftKn = LIVE_DRIFT_KN,
                runDriftDeg = LIVE_DRIFT_DEG,
            ),
        ),
        SeedSpot(
            spot("dune-point", "Dune Point", GeoPointDto(55.0157, 8.4352), listOf(SectorDto(230.0, 20.0)), listOf(SectorDto(90.0, 180.0))),
            ForecastProfile(
                meanKn = 17.0,
                swingKn = 7.0,
                swingPeriodHours = 120,
                seaBreezeKn = 2.0,
                bearingDeg = 270.0,
                bearingSwingDeg = 45.0,
                gustFactor = 0.3,
                modelSpreadKn = 2.0,
                noiseKn = 1.5,
                rainyShare = 0.15,
                temperatureC = 15.0,
                runDriftKn = LIVE_DRIFT_KN,
                runDriftDeg = LIVE_DRIFT_DEG,
            ),
        ),
        SeedSpot(
            spot(
                "harbor-flats",
                "Harbor Flats",
                GeoPointDto(54.3225, 10.1394),
                listOf(SectorDto(30.0, 120.0)),
                listOf(SectorDto(200.0, 290.0)),
            ).copy(dataQuality = DataQualityDto.UNVERIFIED),
            ForecastProfile(
                meanKn = 12.0,
                swingKn = 6.0,
                swingPeriodHours = 84,
                seaBreezeKn = 2.0,
                bearingDeg = 80.0,
                bearingSwingDeg = 60.0,
                gustFactor = 0.35,
                modelSpreadKn = 2.5,
                noiseKn = 2.0,
                rainyShare = 0.2,
                temperatureC = 16.0,
                runDriftKn = LIVE_DRIFT_KN,
                runDriftDeg = LIVE_DRIFT_DEG,
            ),
        ),
    )
