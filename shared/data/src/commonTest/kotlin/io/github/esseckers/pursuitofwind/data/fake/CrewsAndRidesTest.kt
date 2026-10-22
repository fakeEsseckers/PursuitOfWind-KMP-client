package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.CreateCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewDto
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.CrewListDto
import io.github.esseckers.pursuitofwind.contract.CrewMemberListDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RideCancelledEventDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideListDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.contract.SeatReleasedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrewsAndRidesTest {
    private suspend fun FakeApiDriver.join(
        token: String,
        code: String,
    ) = send(ApiRoute.JOIN_CREW, token = token, value = JoinCrewRequestDto(code))

    @Test
    fun `the creator owns a new crew and others join it with the invite code`() =
        runTest {
            val driver = fakeDriver()
            val owner = driver.signIn(1)
            val friend = driver.signIn(2)

            val crew =
                driver
                    .send(
                        ApiRoute.CREATE_CREW,
                        token = owner,
                        value = CreateCrewRequestDto("Night Owls"),
                    ).decode(CrewDto.serializer())
            val joined = driver.join(friend, crew.inviteCode.lowercase())
            val joinedAgain = driver.join(friend, crew.inviteCode)
            val members = driver.call(ApiRoute.LIST_CREW_MEMBERS, crew.id, token = owner).decode(CrewMemberListDto.serializer()).items

            assertEquals("Night Owls", crew.name)
            assertEquals(1, crew.memberCount)
            assertEquals(2, joined.decode(CrewDto.serializer()).memberCount)
            assertEquals(2, joinedAgain.decode(CrewDto.serializer()).memberCount)
            assertEquals(listOf(MemberRoleDto.OWNER, MemberRoleDto.MEMBER), members.map { it.role })
            assertEquals(
                listOf(crew.id),
                driver
                    .call(ApiRoute.LIST_MY_CREWS, token = friend)
                    .decode(CrewListDto.serializer())
                    .items
                    .map { it.id },
            )
        }

    @Test
    fun `wrong invite codes and outsiders are rejected`() =
        runTest {
            val driver = fakeDriver()
            val outsider = driver.signIn()

            assertEquals(ProblemCode.INVITE_INVALID, driver.join(outsider, "WIND-0000").problem().code)
            assertEquals(ProblemCode.NOT_A_MEMBER, driver.call(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = outsider).problem().code)
            assertEquals(ProblemCode.NOT_A_MEMBER, driver.call(ApiRoute.GET_RIDE, "rid_7f3c", token = outsider).problem().code)
        }

    @Test
    fun `members appear with the name from their profile`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            driver.send(ApiRoute.PUT_PROFILE, token = alex, value = ALEX_PROFILE)
            driver.join(alex, "WIND-4KQ7")

            val members = driver.call(ApiRoute.LIST_CREW_MEMBERS, "crw_weekend", token = alex).decode(CrewMemberListDto.serializer()).items

            assertEquals(listOf("Andrei", "Mira", "Jonas", "Alex"), members.map { it.displayName })
        }

    @Test
    fun `seats go until the car is full and every change bumps the version`() =
        runTest {
            val driver = fakeDriver()
            val riders = (1..3).map { driver.signIn(it) }
            riders.forEach { driver.join(it, "WIND-4KQ7") }

            val seeded = driver.call(ApiRoute.GET_RIDE, "rid_7f3c", token = riders[0]).decode(RideDto.serializer())
            val first = driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = riders[0])
            val second = driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = riders[1])
            val full = driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = riders[2])
            val again = driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = riders[0])
            val released = driver.call(ApiRoute.RELEASE_SEAT, "rid_7f3c", token = riders[0]).decode(RideDto.serializer())
            val releasedAgain = driver.call(ApiRoute.RELEASE_SEAT, "rid_7f3c", token = riders[0]).decode(RideDto.serializer())

            assertEquals(2, seeded.seatsFree)
            assertEquals(listOf(201, 201, 409, 409), listOf(first, second, full, again).map { it.status })
            assertEquals(ProblemCode.SEATS_FULL, full.problem().code)
            assertEquals(ProblemCode.ALREADY_JOINED, again.problem().code)
            assertEquals(seeded.version + 3, released.version)
            assertEquals(1, released.seatsFree)
            assertEquals(released, releasedAgain)
        }

    @Test
    fun `only the driver cancels and a cancelled ride takes no passengers`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn(1)
            val mira = driver.signIn(2)
            driver.join(alex, "WIND-4KQ7")
            driver.join(mira, "WIND-4KQ7")
            val ride =
                driver
                    .send(
                        ApiRoute.CREATE_RIDE,
                        "crw_weekend",
                        token = alex,
                        value = rideRequest(2),
                    ).decode(RideDto.serializer())

            val byPassenger = driver.call(ApiRoute.CANCEL_RIDE, ride.id, token = mira)
            val cancelled = driver.call(ApiRoute.CANCEL_RIDE, ride.id, token = alex).decode(RideDto.serializer())
            val cancelledTwice = driver.call(ApiRoute.CANCEL_RIDE, ride.id, token = alex).decode(RideDto.serializer())
            val late = driver.call(ApiRoute.TAKE_SEAT, ride.id, token = mira)
            val listed = driver.call(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = mira).decode(RideListDto.serializer()).items

            assertEquals(ProblemCode.NOT_THE_DRIVER, byPassenger.problem().code)
            assertEquals(RideStatusDto.CANCELLED, cancelled.status)
            assertEquals(cancelled, cancelledTwice)
            assertEquals(ProblemCode.RIDE_CANCELLED, late.problem().code)
            assertEquals(listOf(ride.id, "rid_7f3c"), listed.map { it.id })
        }

    @Test
    fun `rides need a known spot and a sane window and one to eight seats`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            driver.join(alex, "WIND-4KQ7")
            val invalid =
                listOf(
                    rideRequest(2).copy(spotId = "atlantis"),
                    rideRequest(2).copy(windowEnd = rideRequest(2).windowStart),
                    rideRequest(2).copy(departAt = rideRequest(2).windowEnd),
                    rideRequest(0),
                    rideRequest(2).copy(meetingPoint = " "),
                )

            val statuses =
                invalid.map {
                    driver
                        .send(
                            ApiRoute.CREATE_RIDE,
                            "crw_weekend",
                            token = alex,
                            value = it,
                        ).status
                }

            assertEquals(List(invalid.size) { 422 }, statuses)
        }

    @Test
    fun `feed events carry the ride version they produced`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn(1)
            val mira = driver.signIn(2)
            driver.join(alex, "WIND-4KQ7")
            driver.join(mira, "WIND-4KQ7")
            val ride = driver.send(ApiRoute.CREATE_RIDE, "crw_weekend", token = alex, value = rideRequest(2)).decode(RideDto.serializer())

            val versions =
                listOf(
                    driver.call(ApiRoute.TAKE_SEAT, ride.id, token = mira),
                    driver.call(ApiRoute.RELEASE_SEAT, ride.id, token = mira),
                    driver.call(ApiRoute.CANCEL_RIDE, ride.id, token = alex),
                ).map { it.decode(RideDto.serializer()).version }
            val events =
                driver
                    .call(ApiRoute.GET_CREW_FEED, "crw_weekend", token = alex)
                    .decode(CrewFeedDto.serializer())
                    .items
                    .takeLast(3)

            assertEquals(listOf(ride.version + 1, ride.version + 2, ride.version + 3), versions)
            assertEquals(versions[0], assertIs<SeatTakenEventDto>(events[0]).rideVersion)
            assertEquals(versions[1], assertIs<SeatReleasedEventDto>(events[1]).rideVersion)
            assertEquals(versions[2], assertIs<RideCancelledEventDto>(events[2]).rideVersion)
        }

    @Test
    fun `texts are stored as sent and need a character that is not a space`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            val emoji = "\uD83E\uDE81" // one code point, two UTF-16 units

            val padded = driver.send(ApiRoute.CREATE_CREW, token = alex, value = CreateCrewRequestDto("  Lagoon  "))
            val blank = driver.send(ApiRoute.CREATE_CREW, token = alex, value = CreateCrewRequestDto("   "))
            val noBreakSpaces = driver.send(ApiRoute.CREATE_CREW, token = alex, value = CreateCrewRequestDto("\u00A0\u00A0"))
            val longest = driver.send(ApiRoute.CREATE_CREW, token = alex, value = CreateCrewRequestDto(emoji.repeat(40)))
            val tooLong = driver.send(ApiRoute.CREATE_CREW, token = alex, value = CreateCrewRequestDto(emoji.repeat(41)))
            val noCode = driver.join(alex, "")
            val spacedCode = driver.join(alex, "  wind-4kq7 ")
            val blankMeetingPoint =
                driver.send(ApiRoute.CREATE_RIDE, "crw_weekend", token = alex, value = rideRequest(2).copy(meetingPoint = "  "))

            assertEquals("  Lagoon  ", padded.decode(CrewDto.serializer()).name)
            assertEquals(
                listOf(422, 422, 201, 422, 422, 200, 422),
                listOf(blank, noBreakSpaces, longest, tooLong, noCode, spacedCode, blankMeetingPoint).map { it.status },
            )
        }
}
