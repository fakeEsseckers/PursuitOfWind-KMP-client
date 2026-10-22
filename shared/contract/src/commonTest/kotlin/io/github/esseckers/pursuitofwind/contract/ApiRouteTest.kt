package io.github.esseckers.pursuitofwind.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiRouteTest {
    @Test
    fun `operation ids are the camel case of the route names`() {
        assertEquals("signInAnonymously", ApiRoute.SIGN_IN_ANONYMOUSLY.operationId)
        assertEquals("getSpotForecast", ApiRoute.GET_SPOT_FORECAST.operationId)
    }

    @Test
    fun `path parameters are filled in order`() {
        assertEquals(listOf("rideId"), ApiRoute.TAKE_SEAT.pathParameters)
        assertEquals("/v1/rides/rid_7f3c/seats", ApiRoute.TAKE_SEAT.pathWith("rid_7f3c"))
        assertEquals("/v1/me/profile", ApiRoute.GET_PROFILE.pathWith())
        assertFailsWith<IllegalArgumentException> { ApiRoute.TAKE_SEAT.pathWith() }
    }

    @Test
    fun `every parameter is a whole path segment`() {
        ApiRoute.entries.forEach { route ->
            assertEquals(route.path.count { it == '{' }, route.pathParameters.size, route.name)
            assertEquals(route.path.count { it == '}' }, route.pathParameters.size, route.name)
        }
        assertEquals(listOf("crewId"), ApiRoute.LIST_CREW_RIDES.pathParameters)
        assertEquals("/v1/rides/rid_1/seats/me", ApiRoute.RELEASE_SEAT.pathWith("rid_1"))
    }

    @Test
    fun `only the token endpoints work without a token`() {
        assertFalse(ApiRoute.REFRESH_TOKENS.requiresAuth)
        assertTrue(ApiRoute.entries.filter { it.requiresAuth }.size == ApiRoute.entries.size - 2)
    }
}
