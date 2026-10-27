package io.github.esseckers.pursuitofwind.feature.crew.presentation.preview

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_invalid
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.CrewMember
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MemberRole
import io.github.esseckers.pursuitofwind.data.model.Person
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.crew.domain.entity.CrewDetailsEntity
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel.CrewReducer
import io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel.CrewResult
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * States of the Crew tab for previews and UI tests, on Thursday morning in Berlin. Alex is in Andrei's Weekend Crew
 * and in Night Owls. Saturday: Andrei drives to Nordstrand Bay with Mira and one seat free; Jonas's car to Salt Lagoon
 * is full. Friday: Alex drives to Lake Weiss with Jonas. A ride Mira cancelled stays, crossed out. Built by the real
 * reducer.
 */
internal object CrewPreviewData {
    val zone: TimeZone = TimeZone.of("Europe/Berlin")
    val now: Instant = Instant.parse("2026-10-22T06:00:00Z")

    private val fetchedAt = now - 10.minutes
    private val friday = Instant.parse("2026-10-23T00:00:00Z")
    private val saturday = friday + 1.days

    val alex = Person("usr_alex", "Alex")
    val andrei = Person("usr_andrei", "Andrei")
    val mira = Person("usr_mira", "Mira")
    val jonas = Person("usr_jonas", "Jonas")

    val weekendCrew = Crew("crw_weekend", "Weekend Crew", "WIND-4KQ7", memberCount = 4, createdAt = now - 30.days)
    val nightOwls = Crew("crw_owls", "Night Owls", "WIND-7HPX", memberCount = 2, createdAt = now - 3.days)

    val nordstrand = spot("nordstrand-bay", "Nordstrand Bay")
    val saltLagoon = spot("salt-lagoon", "Salt Lagoon")
    val lakeWeiss = spot("lake-weiss", "Lake Weiss")

    /** Andrei's car on Saturday: Mira is in, one seat is free. */
    val andreisRide = ride("rid_andrei", nordstrand, andrei, saturday + 11.hours, seats = 2, passengers = listOf(mira))

    val members =
        listOf(
            CrewMember(andrei.userId, andrei.displayName, MemberRole.OWNER, now - 30.days),
            CrewMember(mira.userId, mira.displayName, MemberRole.MEMBER, now - 29.days),
            CrewMember(jonas.userId, jonas.displayName, MemberRole.MEMBER, now - 28.days),
            CrewMember(alex.userId, alex.displayName, MemberRole.MEMBER, now - 2.days),
        )

    val rides =
        listOf(
            andreisRide,
            ride("rid_jonas", saltLagoon, jonas, saturday + 12.hours, seats = 2, passengers = listOf(andrei, mira)),
            ride("rid_alex", lakeWeiss, alex, friday + 13.hours, seats = 3, passengers = listOf(jonas)),
            ride("rid_mira", saltLagoon, mira, friday + 9.hours, seats = 2, passengers = emptyList(), status = RideStatus.CANCELLED),
        )

    val details =
        CrewDetailsEntity(
            crewId = weekendCrew.id,
            members = Cached(members, fetchedAt),
            rides = Cached(rides, fetchedAt),
            spots = listOf(nordstrand, saltLagoon, lakeWeiss).associateBy { it.id },
            userId = alex.userId,
        )

    val loading = CrewState()

    val ready =
        reduce(
            CrewResult.Ticked(now),
            CrewResult.CrewsChanged(Cached(listOf(weekendCrew, nightOwls), fetchedAt)),
            CrewResult.Chosen(weekendCrew.id),
            CrewResult.DetailsChanged(details),
            CrewResult.RefreshFinished(null),
        )

    /** Alex's tap on "Take a seat" in Andrei's car, waiting for the server. */
    val takingSeat =
        reduce(
            CrewResult.DetailsChanged(
                details.copy(
                    rides =
                        Cached(
                            rides.map {
                                if (it ==
                                    andreisRide
                                ) {
                                    it.copy(pending = true)
                                } else {
                                    it
                                }
                            },
                            fetchedAt,
                        ),
                ),
            ),
            from = ready,
        )

    /** The saved crew shown while offline. */
    val offline = reduce(CrewResult.RefreshFinished(AppError.Network), from = ready)

    /** Alex belongs to no crew. */
    val noCrew = reduce(CrewResult.CrewsChanged(Cached(emptyList(), fetchedAt)), CrewResult.RefreshFinished(null))

    /** Nothing cached and no connection. */
    val error = reduce(CrewResult.CrewsChanged(Cached(emptyList(), null)), CrewResult.RefreshFinished(AppError.Network))

    val creating = reduce(CrewResult.DialogShown(CrewDialog.Create()), from = noCrew)

    /** A code no crew has. */
    val joinFailed = reduce(CrewResult.DialogShown(CrewDialog.Join(error = Res.string.crew_invite_invalid)), from = noCrew)

    /** Alex is about to cancel the Friday ride, which Jonas counts on. */
    val confirmingCancel =
        reduce(CrewResult.DialogShown(CrewDialog.ConfirmCancel("rid_alex", lakeWeiss.name, passengers = 1)), from = ready)

    private fun reduce(
        vararg results: CrewResult,
        from: CrewState = CrewState(),
    ): CrewState = results.fold(from, CrewReducer(zone)::reduce)

    @Suppress("LongParameterList") // A ride has that many parts; named arguments keep each call readable.
    private fun ride(
        id: String,
        spot: Spot,
        driver: Person,
        windowStart: Instant,
        seats: Int,
        passengers: List<Person>,
        status: RideStatus = RideStatus.SCHEDULED,
    ): Ride =
        Ride(
            id = id,
            version = 1 + passengers.size,
            crewId = weekendCrew.id,
            spotId = spot.id,
            driver = driver,
            windowStart = windowStart,
            windowEnd = windowStart + 4.hours,
            departAt = windowStart - 2.hours - 30.minutes,
            meetingPoint = "Hauptbahnhof, north exit",
            seatsTotal = seats,
            seatsFree = seats - passengers.size,
            passengers = passengers,
            status = status,
            createdAt = now - 1.days,
        )

    private fun spot(
        id: String,
        name: String,
    ): Spot =
        Spot(
            id = SpotId(id),
            name = name,
            location = GeoPoint(54.5, 8.8),
            timeZone = zone,
            rideableSectors = listOf(Sector.of(180.0, 300.0)),
            dangerSectors = emptyList(),
            water = Bearing(240.0),
            createdBy = "usr_andrei",
        )
}
