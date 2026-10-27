package io.github.esseckers.pursuitofwind.data.internal.network

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.CreateCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.CreateRideRequestDto
import io.github.esseckers.pursuitofwind.contract.CreateSpotRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewDto
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.CrewListDto
import io.github.esseckers.pursuitofwind.contract.CrewMemberListDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.MySpotDto
import io.github.esseckers.pursuitofwind.contract.MySpotListDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.PushTokenRequestDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideListDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.contract.SpotForecastDto
import io.github.esseckers.pursuitofwind.contract.SpotPageDto
import io.github.esseckers.pursuitofwind.contract.SpotUpdateDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Instant

/**
 * The authenticated operations of `api/openapi.yaml`, one function each, on DTOs. Failures throw [ApiException] or the
 * engine's exception; repositories turn both into `AppError`s. Mutations take an `Idempotency-Key` that the caller
 * creates once per user action, so a retry of the same action is answered from the server's record.
 */
@Suppress("TooManyFunctions") // One function per operation of the contract.
internal class PursuitOfWindApi(
    private val client: HttpClient,
) {
    /** Null when the rider has not saved a profile yet (`404`). */
    suspend fun profile(): RiderProfileDto? {
        val response = send(ApiRoute.GET_PROFILE)
        return if (response.status.value == NOT_FOUND) null else response.ok().body()
    }

    suspend fun putProfile(
        profile: RiderProfileDto,
        key: String,
    ): RiderProfileDto = send(ApiRoute.PUT_PROFILE, key = key) { json(profile) }.ok().body()

    suspend fun spotCatalogPage(
        cursor: String?,
        limit: Int,
    ): SpotPageDto =
        send(ApiRoute.LIST_SPOT_CATALOG) {
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }.ok().body()

    suspend fun createSpot(
        request: CreateSpotRequestDto,
        key: String,
    ): SpotDto = send(ApiRoute.CREATE_SPOT, key = key) { json(request) }.ok().body()

    suspend fun updateSpot(
        spotId: String,
        update: SpotUpdateDto,
        key: String,
    ): SpotDto = send(ApiRoute.UPDATE_SPOT, spotId, key = key) { json(update) }.ok().body()

    suspend fun mySpots(): MySpotListDto = send(ApiRoute.LIST_MY_SPOTS).ok().body()

    suspend fun putMySpot(
        spotId: String,
        update: MySpotUpdateDto,
        key: String,
    ): MySpotDto = send(ApiRoute.PUT_MY_SPOT, spotId, key = key) { json(update) }.ok().body()

    suspend fun deleteMySpot(
        spotId: String,
        key: String,
    ) {
        send(ApiRoute.DELETE_MY_SPOT, spotId, key = key).ok()
    }

    suspend fun forecast(
        spotId: String,
        from: Instant,
        hours: Int,
    ): SpotForecastDto =
        send(ApiRoute.GET_SPOT_FORECAST, spotId) {
            parameter("from", from.toString())
            parameter("hours", hours)
        }.ok().body()

    suspend fun createCrew(
        name: String,
        key: String,
    ): CrewDto = send(ApiRoute.CREATE_CREW, key = key) { json(CreateCrewRequestDto(name)) }.ok().body()

    suspend fun myCrews(): CrewListDto = send(ApiRoute.LIST_MY_CREWS).ok().body()

    suspend fun joinCrew(
        inviteCode: String,
        key: String,
    ): CrewDto = send(ApiRoute.JOIN_CREW, key = key) { json(JoinCrewRequestDto(inviteCode)) }.ok().body()

    suspend fun members(crewId: String): CrewMemberListDto = send(ApiRoute.LIST_CREW_MEMBERS, crewId).ok().body()

    suspend fun rides(
        crewId: String,
        from: Instant,
    ): RideListDto = send(ApiRoute.LIST_CREW_RIDES, crewId) { parameter("from", from.toString()) }.ok().body()

    suspend fun createRide(
        crewId: String,
        ride: CreateRideRequestDto,
        key: String,
    ): RideDto = send(ApiRoute.CREATE_RIDE, crewId, key = key) { json(ride) }.ok().body()

    suspend fun ride(rideId: String): RideDto = send(ApiRoute.GET_RIDE, rideId).ok().body()

    suspend fun cancelRide(
        rideId: String,
        key: String,
    ): RideDto = send(ApiRoute.CANCEL_RIDE, rideId, key = key).ok().body()

    suspend fun takeSeat(
        rideId: String,
        key: String,
    ): RideDto = send(ApiRoute.TAKE_SEAT, rideId, key = key).ok().body()

    suspend fun releaseSeat(
        rideId: String,
        key: String,
    ): RideDto = send(ApiRoute.RELEASE_SEAT, rideId, key = key).ok().body()

    suspend fun feed(
        crewId: String,
        since: String?,
        limit: Int,
    ): CrewFeedDto =
        send(ApiRoute.GET_CREW_FEED, crewId) {
            since?.let { parameter("since", it) }
            parameter("limit", limit)
        }.ok().body()

    suspend fun putPushToken(
        request: PushTokenRequestDto,
        key: String,
    ) {
        send(ApiRoute.PUT_PUSH_TOKEN, key = key) { json(request) }.ok()
    }

    private suspend fun send(
        route: ApiRoute,
        vararg pathParameters: String,
        key: String? = null,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse =
        client.request(route.pathWith(*pathParameters)) {
            method = HttpMethod.parse(route.method)
            key?.let { header(ApiHeaders.IDEMPOTENCY_KEY, it) }
            configure()
        }

    private suspend fun HttpResponse.ok(): HttpResponse = if (status.isSuccess()) this else throw ApiException.from(this)

    private inline fun <reified T> HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private companion object {
        const val NOT_FOUND = 404
    }
}
