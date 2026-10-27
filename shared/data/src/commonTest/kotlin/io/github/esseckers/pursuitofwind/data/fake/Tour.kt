package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.AnonymousSignInRequestDto
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.CreateCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.CreateSpotRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewDto
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.GeoPointDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.contract.PushTokenRequestDto
import io.github.esseckers.pursuitofwind.contract.RefreshTokensRequestDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.contract.SkillLevelDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.contract.SpotPageDto
import io.github.esseckers.pursuitofwind.contract.SpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.WindUnitDto
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

val ALEX_PROFILE =
    RiderProfileDto(
        displayName = "Alex",
        weightKg = 80.0,
        level = SkillLevelDto.INTERMEDIATE,
        quiverM2 = listOf(7.0, 9.0, 12.0),
        minSessionMinutes = 120,
        maxDriveMinutes = 180,
        windUnit = WindUnitDto.KNOTS,
    )

/** A spot a rider adds on the North Sea coast, its water to the west-south-west. */
val SANDBANK =
    CreateSpotRequestDto(
        name = "Sandbank North",
        location = GeoPointDto(54.6102, 8.3569),
        waterBearingDeg = 250.0,
        timeZone = "Europe/Berlin",
    )

fun rideRequest(seats: Int) =
    CreateRideRequestDto(
        spotId = "salt-lagoon",
        windowStart = THURSDAY_MORNING + 2.days + 5.hours,
        windowEnd = THURSDAY_MORNING + 2.days + 9.hours,
        departAt = THURSDAY_MORNING + 2.days + 2.hours,
        meetingPoint = "Parking at the station",
        seatsTotal = seats,
    )

/**
 * Calls every route with its success and error cases and checks the statuses. The JVM contract test replays the
 * recorded exchanges against `api/openapi.yaml` and checks that every documented status was reached.
 */
suspend fun FakeApiDriver.tourEveryRoute() = Tour(this).run()

/** Alex, Mira and Jonas sign in, set up crews and rides, and try everything they should not be able to do. */
private class Tour(
    private val driver: FakeApiDriver,
) {
    private lateinit var alex: String
    private lateinit var mira: String
    private lateinit var jonas: String
    private lateinit var miraCrew: CrewDto

    suspend fun run() {
        signIn()
        profile()
        spots()
        riderSpots()
        crews()
        seededRide()
        newRide()
        feed()
        retries()
        tokens()
        authenticationAndKeys()
    }

    private suspend fun signIn() {
        alex = driver.signIn(1)
        expect(200) { send(ApiRoute.SIGN_IN_ANONYMOUSLY, token = null, value = anon(1)) }
        expect(422) { send(ApiRoute.SIGN_IN_ANONYMOUSLY, token = null, value = anon(1).copy(installationId = "phone")) }
        mira = driver.signIn(2)
        jonas = driver.signIn(3)
    }

    private suspend fun profile() {
        expect(404) { call(ApiRoute.GET_PROFILE, token = alex) }
        expect(422) { send(ApiRoute.PUT_PROFILE, token = alex, value = ALEX_PROFILE.copy(weightKg = 20.0)) }
        expect(200) { send(ApiRoute.PUT_PROFILE, token = alex, value = ALEX_PROFILE) }
        expect(200) { call(ApiRoute.GET_PROFILE, token = alex) }
        expect(204) { send(ApiRoute.PUT_PUSH_TOKEN, token = alex, value = PushTokenRequestDto(PlatformDto.IOS, "tok")) }
    }

    private suspend fun spots() {
        val firstPage = expect(200) { get(ApiRoute.LIST_SPOT_CATALOG, token = alex, query = mapOf("limit" to "2")) }
        val cursor = requireNotNull(firstPage.decode(SpotPageDto.serializer()).nextCursor)
        expect(200) { get(ApiRoute.LIST_SPOT_CATALOG, token = alex, query = mapOf("cursor" to cursor, "limit" to "2")) }
        expect(422) { get(ApiRoute.LIST_SPOT_CATALOG, token = alex, query = mapOf("limit" to "0")) }

        val follow = MySpotUpdateDto(driveMinutes = 120, favorite = true)
        expect(404) { send(ApiRoute.PUT_MY_SPOT, "atlantis", token = alex, value = follow) }
        expect(422) { send(ApiRoute.PUT_MY_SPOT, "salt-lagoon", token = alex, value = follow.copy(driveMinutes = 5000)) }
        expect(200) { send(ApiRoute.PUT_MY_SPOT, "nordstrand-bay", token = alex, value = follow) }
        expect(200) { call(ApiRoute.LIST_MY_SPOTS, token = alex) }
        expect(204) { call(ApiRoute.DELETE_MY_SPOT, "nordstrand-bay", token = alex, idempotencyKey = FakeApiDriver.key(1)) }

        expect(200) { get(ApiRoute.GET_SPOT_FORECAST, "nordstrand-bay", token = alex, query = mapOf("hours" to "24")) }
        expect(422) { get(ApiRoute.GET_SPOT_FORECAST, "nordstrand-bay", token = alex, query = mapOf("hours" to "500")) }
        expect(404) { call(ApiRoute.GET_SPOT_FORECAST, "atlantis", token = alex) }
    }

    /** Alex adds a spot, gets its forecast and moves it; Mira cannot change it. */
    private suspend fun riderSpots() {
        val added = expect(201) { send(ApiRoute.CREATE_SPOT, token = alex, value = SANDBANK) }.decode(SpotDto.serializer())
        expect(422) { send(ApiRoute.CREATE_SPOT, token = alex, value = SANDBANK.copy(waterBearingDeg = 360.0)) }
        expect(422) { send(ApiRoute.CREATE_SPOT, token = alex, value = SANDBANK.copy(timeZone = "Mars/Olympus")) }
        expect(422) { send(ApiRoute.CREATE_SPOT, token = alex, value = SANDBANK.copy(timeZone = "+02:00")) }
        expect(200) { get(ApiRoute.GET_SPOT_FORECAST, added.id, token = alex, query = mapOf("hours" to "24")) }
        val moved = SpotUpdateDto(added.name, GeoPointDto(54.6115, 8.3548), waterBearingDeg = 235.0)
        expect(200) { send(ApiRoute.UPDATE_SPOT, added.id, token = alex, value = moved) }
        expect(403) { send(ApiRoute.UPDATE_SPOT, added.id, token = mira, value = moved) }
        expect(404) { send(ApiRoute.UPDATE_SPOT, "atlantis", token = alex, value = moved) }
        expect(422) { send(ApiRoute.UPDATE_SPOT, added.id, token = alex, value = moved.copy(name = " ")) }
    }

    private suspend fun crews() {
        val created = expect(201) { send(ApiRoute.CREATE_CREW, token = mira, value = CreateCrewRequestDto("Lagoon Kiters")) }
        miraCrew = created.decode(CrewDto.serializer())
        expect(200) { call(ApiRoute.LIST_MY_CREWS, token = mira) }
        expect(404) { send(ApiRoute.JOIN_CREW, token = alex, value = JoinCrewRequestDto("NOPE-0000")) }
        expect(200) { send(ApiRoute.JOIN_CREW, token = alex, value = JoinCrewRequestDto("WIND-4KQ7")) }
        expect(200) { send(ApiRoute.JOIN_CREW, token = jonas, value = JoinCrewRequestDto(miraCrew.inviteCode)) }

        expect(200) { call(ApiRoute.LIST_CREW_MEMBERS, "crw_weekend", token = alex) }
        expect(403) { call(ApiRoute.LIST_CREW_MEMBERS, miraCrew.id, token = alex) }
        expect(404) { call(ApiRoute.LIST_CREW_MEMBERS, "crw_nope", token = alex) }
        expect(200) { call(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = alex) }
        expect(403) { call(ApiRoute.LIST_CREW_RIDES, miraCrew.id, token = alex) }
        expect(404) { call(ApiRoute.LIST_CREW_RIDES, "crw_nope", token = alex) }
        expect(422) { get(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = alex, query = mapOf("from" to "tomorrow")) }
    }

    /** Alex takes and releases a seat in Andrei's seeded ride; the crew feed then holds every kind of event but one. */
    private suspend fun seededRide() {
        expect(200) { call(ApiRoute.GET_RIDE, "rid_7f3c", token = alex) }
        expect(201) { call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = alex) }
        expect(409) { call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = alex) }
        expect(200) { call(ApiRoute.RELEASE_SEAT, "rid_7f3c", token = alex) }
        expect(200) { get(ApiRoute.GET_CREW_FEED, "crw_weekend", token = alex, query = mapOf("limit" to "100")) }
        listOf(ApiRoute.GET_RIDE, ApiRoute.TAKE_SEAT, ApiRoute.RELEASE_SEAT, ApiRoute.CANCEL_RIDE).forEach { route ->
            expect(404) { call(route, "rid_nope", token = alex) }
        }
    }

    /** Mira offers one seat in her crew; Jonas takes it, Alex is not a member yet, then the ride is cancelled. */
    private suspend fun newRide() {
        val ride =
            expect(201) {
                send(ApiRoute.CREATE_RIDE, miraCrew.id, token = mira, value = rideRequest(seats = 1))
            }.decode(RideDto.serializer())
        expect(422) { send(ApiRoute.CREATE_RIDE, miraCrew.id, token = mira, value = rideRequest(seats = 9)) }
        expect(403) { send(ApiRoute.CREATE_RIDE, miraCrew.id, token = alex, value = rideRequest(seats = 1)) }
        expect(404) { send(ApiRoute.CREATE_RIDE, "crw_nope", token = alex, value = rideRequest(seats = 1)) }
        expect(201) { call(ApiRoute.TAKE_SEAT, ride.id, token = jonas) }

        listOf(ApiRoute.GET_RIDE, ApiRoute.TAKE_SEAT, ApiRoute.RELEASE_SEAT).forEach { route ->
            expect(403) { call(route, ride.id, token = alex) }
        }
        expect(403) { call(ApiRoute.GET_CREW_FEED, miraCrew.id, token = alex) }
        expect(200) { send(ApiRoute.JOIN_CREW, token = alex, value = JoinCrewRequestDto(miraCrew.inviteCode)) }
        expect(409) { call(ApiRoute.TAKE_SEAT, ride.id, token = alex) }

        expect(403) { call(ApiRoute.CANCEL_RIDE, ride.id, token = jonas) }
        expect(200) { call(ApiRoute.CANCEL_RIDE, ride.id, token = mira) }
        expect(409) { call(ApiRoute.TAKE_SEAT, ride.id, token = alex) }
    }

    private suspend fun feed() {
        expect(200) { call(ApiRoute.GET_CREW_FEED, miraCrew.id, token = mira) }
        val page =
            expect(200) {
                get(ApiRoute.GET_CREW_FEED, miraCrew.id, token = mira, query = mapOf("limit" to "2"))
            }.decode(CrewFeedDto.serializer())
        expect(200) { get(ApiRoute.GET_CREW_FEED, miraCrew.id, token = mira, query = mapOf("since" to page.items.first().id)) }
        expect(410) { get(ApiRoute.GET_CREW_FEED, miraCrew.id, token = mira, query = mapOf("since" to "evt_gone")) }
        expect(404) { call(ApiRoute.GET_CREW_FEED, "crw_nope", token = mira) }
        expect(422) { get(ApiRoute.GET_CREW_FEED, miraCrew.id, token = mira, query = mapOf("limit" to "0")) }
    }

    /** A retry with the same key is replayed; the same key with another body, or a key that is not a UUID, is refused. */
    private suspend fun retries() {
        val crew = CreateCrewRequestDto("Retried Crew")
        expect(201) { send(ApiRoute.CREATE_CREW, token = jonas, value = crew, idempotencyKey = FakeApiDriver.key(2)) }
        expect(201) { send(ApiRoute.CREATE_CREW, token = jonas, value = crew, idempotencyKey = FakeApiDriver.key(2)) }
        expect(422) { send(ApiRoute.CREATE_CREW, token = jonas, value = crew.copy(name = "Other"), idempotencyKey = FakeApiDriver.key(2)) }
        expect(422) { send(ApiRoute.CREATE_CREW, token = jonas, value = crew, idempotencyKey = "not-a-uuid") }
    }

    private suspend fun tokens() {
        val tokens = expect(201) { send(ApiRoute.SIGN_IN_ANONYMOUSLY, token = null, value = anon(4)) }.decode(AuthTokensDto.serializer())
        val refresh = RefreshTokensRequestDto(tokens.refreshToken)
        expect(200) { send(ApiRoute.REFRESH_TOKENS, token = null, value = refresh) }
        expect(401) { send(ApiRoute.REFRESH_TOKENS, token = null, value = refresh) }
        expect(422) { call(ApiRoute.REFRESH_TOKENS, body = "{}") }
    }

    /** Every authenticated route refuses a missing token, and every mutation an `Idempotency-Key` that is not a UUID. */
    private suspend fun authenticationAndKeys() {
        ApiRoute.entries.filter { it.requiresAuth }.forEach { route ->
            val parameters = route.pathParameters.map { "x" }.toTypedArray()
            expect(401) { call(route, *parameters) }
            if (route.method != "GET") expect(422) { call(route, *parameters, token = alex, idempotencyKey = "not-a-uuid") }
        }
    }

    private suspend fun expect(
        status: Int,
        request: suspend FakeApiDriver.() -> FakeExchange,
    ): FakeExchange = driver.request().also { assertEquals(status, it.status, it.toString()) }

    private fun anon(installation: Int) = AnonymousSignInRequestDto(FakeApiDriver.installationId(installation), PlatformDto.ANDROID)
}
