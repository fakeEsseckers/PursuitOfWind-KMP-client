package io.github.esseckers.pursuitofwind.contract

/**
 * Every operation of `api/openapi.yaml`, so the client, the in-app fake server and the future server share one route
 * table. A contract test keeps it identical to the spec.
 *
 * @property method HTTP method in upper case.
 * @property path path template with `{name}` parameters.
 */
public enum class ApiRoute(
    public val method: String,
    public val path: String,
) {
    SIGN_IN_ANONYMOUSLY("POST", "/v1/auth/anonymous"),
    REFRESH_TOKENS("POST", "/v1/auth/refresh"),
    GET_PROFILE("GET", "/v1/me/profile"),
    PUT_PROFILE("PUT", "/v1/me/profile"),
    LIST_SPOT_CATALOG("GET", "/v1/spots/catalog"),
    LIST_MY_SPOTS("GET", "/v1/me/spots"),
    PUT_MY_SPOT("PUT", "/v1/me/spots/{spotId}"),
    DELETE_MY_SPOT("DELETE", "/v1/me/spots/{spotId}"),
    GET_SPOT_FORECAST("GET", "/v1/spots/{spotId}/forecast"),
    CREATE_CREW("POST", "/v1/crews"),
    LIST_MY_CREWS("GET", "/v1/me/crews"),
    JOIN_CREW("POST", "/v1/crews/join"),
    LIST_CREW_MEMBERS("GET", "/v1/crews/{crewId}/members"),
    LIST_CREW_RIDES("GET", "/v1/crews/{crewId}/rides"),
    CREATE_RIDE("POST", "/v1/crews/{crewId}/rides"),
    GET_RIDE("GET", "/v1/rides/{rideId}"),
    CANCEL_RIDE("DELETE", "/v1/rides/{rideId}"),
    TAKE_SEAT("POST", "/v1/rides/{rideId}/seats"),
    RELEASE_SEAT("DELETE", "/v1/rides/{rideId}/seats/me"),
    GET_CREW_FEED("GET", "/v1/crews/{crewId}/feed"),
    PUT_PUSH_TOKEN("PUT", "/v1/me/push-token"),
    ;

    /** The spec's `operationId`: `SIGN_IN_ANONYMOUSLY` is `signInAnonymously`. */
    public val operationId: String
        get() =
            name
                .lowercase()
                .split('_')
                .mapIndexed { index, word -> if (index == 0) word else word.replaceFirstChar(Char::uppercaseChar) }
                .joinToString("")

    /** Names of the path parameters, in order. */
    public val pathParameters: List<String>
        get() = PATH_PARAMETER.findAll(path).map { it.groupValues[1] }.toList()

    /** False only for the two auth calls that hand out tokens. */
    public val requiresAuth: Boolean
        get() = this != SIGN_IN_ANONYMOUSLY && this != REFRESH_TOKENS

    /**
     * The path with its parameters filled in order: `TAKE_SEAT.pathWith("rid_7f3c")` is `/v1/rides/rid_7f3c/seats`.
     * Values are inserted as they are; ids in path parameters are URL-safe by contract.
     */
    public fun pathWith(vararg values: String): String {
        require(values.size == pathParameters.size) { "$name needs ${pathParameters.size} path parameters, got ${values.size}" }
        val remaining = values.iterator()
        return PATH_PARAMETER.replace(path) { remaining.next() }
    }

    private companion object {
        val PATH_PARAMETER = Regex("\\{([^}]+)}")
    }
}
