package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.data.fake.Seed
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.hours

/**
 * The fake backend behind the HTTP layer: authentication, idempotency and one handler per [ApiRoute].
 * Not thread-safe; the server calls it under a mutex.
 */
internal class FakeApi(
    seed: Seed,
) {
    private val state = FakeState(seed)
    private val auth = AuthRoutes(state)
    private val profile = ProfileRoutes(state)
    private val spots = SpotRoutes(state, forecastSeed = seed.randomSeed)
    private val crews = CrewRoutes(state)
    private val life = CrewLife(state, seed.memberActivity, seed.randomSeed)
    private val rides = RideRoutes(state, life)

    private val handlers: Map<ApiRoute, (FakeCall) -> FakeResponse> =
        mapOf(
            ApiRoute.SIGN_IN_ANONYMOUSLY to auth::signIn,
            ApiRoute.REFRESH_TOKENS to auth::refresh,
            ApiRoute.GET_PROFILE to profile::get,
            ApiRoute.PUT_PROFILE to profile::put,
            ApiRoute.LIST_SPOT_CATALOG to spots::catalog,
            ApiRoute.CREATE_SPOT to spots::create,
            ApiRoute.UPDATE_SPOT to spots::update,
            ApiRoute.LIST_MY_SPOTS to spots::mySpots,
            ApiRoute.PUT_MY_SPOT to spots::follow,
            ApiRoute.DELETE_MY_SPOT to spots::unfollow,
            ApiRoute.GET_SPOT_FORECAST to spots::forecast,
            ApiRoute.CREATE_CREW to crews::create,
            ApiRoute.LIST_MY_CREWS to crews::mine,
            ApiRoute.JOIN_CREW to crews::join,
            ApiRoute.LIST_CREW_MEMBERS to crews::members,
            ApiRoute.GET_CREW_FEED to crews::feed,
            ApiRoute.LIST_CREW_RIDES to rides::list,
            ApiRoute.CREATE_RIDE to rides::create,
            ApiRoute.GET_RIDE to rides::get,
            ApiRoute.CANCEL_RIDE to rides::cancel,
            ApiRoute.TAKE_SEAT to rides::takeSeat,
            ApiRoute.RELEASE_SEAT to rides::releaseSeat,
            ApiRoute.PUT_PUSH_TOKEN to profile::putPushToken,
        )

    init {
        check(handlers.keys == ApiRoute.entries.toSet()) { "Fake routes missing: ${ApiRoute.entries - handlers.keys}" }
    }

    fun handle(call: FakeCall): FakeResponse {
        // What the crew members did since the last request happens first, so every answer shows it.
        life.catchUp(call.now)
        val userId = if (call.route.requiresAuth) auth.authenticate(call.bearerToken, call.now) else ""
        // Only authenticated mutations declare Idempotency-Key; on the token endpoints the header is ignored.
        val key = call.idempotencyKey?.takeIf { call.route.requiresAuth && call.route.method != "GET" }
        return when {
            userId == null -> unauthorized(tokenSent = call.bearerToken != null)
            key == null -> run(call.copy(userId = userId))
            else -> idempotent(call.copy(userId = userId), key)
        }
    }

    /**
     * Stores the first response per user, method, path and key for 24 hours. A retry with the same body gets it back
     * with `Idempotent-Replayed: true`; a different body is `422 IDEMPOTENCY_KEY_REUSED`. A `5xx` is not stored, so a
     * retry runs again.
     */
    private fun idempotent(
        call: FakeCall,
        key: String,
    ): FakeResponse {
        state.idempotency.entries.removeAll { it.value.storedAt + KEY_RETENTION <= call.now }
        val scope = IdempotencyScope(call.userId, call.route.method, call.path, key)
        val stored = state.idempotency[scope]
        return when {
            !Validation.isUuid(key) -> {
                invalid("${ApiHeaders.IDEMPOTENCY_KEY} must be a UUID").response
            }

            stored == null -> {
                run(call).also { if (it.status.value < SERVER_ERROR) state.idempotency[scope] = StoredResponse(call.body, it, call.now) }
            }

            stored.requestBody == call.body -> {
                stored.response.withHeader(ApiHeaders.IDEMPOTENT_REPLAYED, "true")
            }

            else -> {
                problem(
                    HttpStatusCode.UnprocessableEntity,
                    ProblemCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key was used for a different request",
                ).response
            }
        }
    }

    /** A bug in a handler becomes `500 INTERNAL`, as on a real server, instead of an exception inside the HTTP client. */
    @Suppress("TooGenericExceptionCaught")
    private fun run(call: FakeCall): FakeResponse =
        try {
            handlers.getValue(call.route)(call)
        } catch (e: ProblemException) {
            e.response
        } catch (e: Exception) {
            problem(HttpStatusCode.InternalServerError, ProblemCode.INTERNAL, "Internal error", e.toString(), e).response
        }

    private companion object {
        val KEY_RETENTION = 24.hours
        const val SERVER_ERROR = 500
    }
}
