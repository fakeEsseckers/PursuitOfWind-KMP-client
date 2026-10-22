package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouterTest {
    @Test
    fun `every route matches its own path with parameters filled in`() {
        ApiRoute.entries.forEach { route ->
            val values = route.pathParameters.map { "x_$it" }

            val match = Router.match(route.method, route.pathWith(*values.toTypedArray()))

            assertEquals(route, match?.route, route.name)
            assertEquals(route.pathParameters.zip(values).toMap(), match?.pathParameters)
        }
    }

    @Test
    fun `fixed segments must match exactly and unknown paths or methods do not match`() {
        assertEquals(ApiRoute.JOIN_CREW, Router.match("POST", "/v1/crews/join")?.route)
        assertNull(Router.match("GET", "/v1/crews/join"))
        assertNull(Router.match("PATCH", "/v1/me/profile"))
        assertNull(Router.match("GET", "/v1/rides//seats"))
        assertNull(Router.match("GET", "/v2/me/profile"))
    }
}
