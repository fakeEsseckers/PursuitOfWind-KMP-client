package io.github.esseckers.pursuitofwind.testing.openapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiSpecTest {
    private val join = RecordedRequest("POST", "/v1/crews/join", "application/json", """{"inviteCode":"WIND-4KQ7"}""")
    private val getProfile = RecordedRequest("GET", "/v1/me/profile", null, "")
    private val unauthorized = """{"type":"/problems/unauthorized","title":"Unauthorized","status":401,"code":"UNAUTHORIZED"}"""

    @Test
    fun `a documented response passes and an undocumented status fails`() {
        val invalidInvite =
            RecordedResponse(
                status = 404,
                contentType = "application/problem+json",
                headerNames = emptySet(),
                body = """{"type":"/problems/invite-invalid","title":"Invite code is not valid","status":404,"code":"INVITE_INVALID"}""",
            )

        assertEquals(emptyList(), OpenApiSpec.checkResponse(join, invalidInvite))
        assertTrue(
            OpenApiSpec
                .checkResponse(
                    join,
                    RecordedResponse(418, "application/json", emptySet(), "{}"),
                ).single()
                .contains("not documented"),
        )
    }

    @Test
    fun `required headers, content types and empty bodies are checked`() {
        val noHeader = RecordedResponse(401, "application/problem+json", emptySet(), unauthorized)
        val wrongType = RecordedResponse(401, "text/plain", setOf("WWW-Authenticate"), unauthorized)
        val unfollow = RecordedRequest("DELETE", "/v1/me/spots/{spotId}", null, "")

        assertTrue(OpenApiSpec.checkResponse(getProfile, noHeader).single().contains("WWW-Authenticate"))
        assertTrue(OpenApiSpec.checkResponse(getProfile, wrongType).single().contains("content type"))
        assertTrue(OpenApiSpec.checkResponse(unfollow, RecordedResponse(204, null, emptySet(), "{}")).single().contains("no body"))
    }

    @Test
    fun `request bodies are validated against their schema`() {
        val problems = OpenApiSpec.checkRequest(RecordedRequest("POST", "/v1/crews", "application/json", """{"name":""}"""))

        assertTrue(problems.isNotEmpty() && problems.all { "name" in it }, problems.toString())
        assertEquals(emptyList(), OpenApiSpec.checkRequest(join))
    }

    @Test
    fun `path, query and header parameters are validated against their schema`() {
        fun catalog(vararg query: Pair<String, String>) = RecordedRequest("GET", "/v1/spots/catalog", null, "", query.toMap())

        fun feed(crewId: String) = RecordedRequest("GET", "/v1/crews/{crewId}/feed", null, "", mapOf("crewId" to crewId))

        assertEquals(emptyList(), OpenApiSpec.checkRequest(catalog("limit" to "50", "cursor" to "c_salt-lagoon")))
        assertTrue(OpenApiSpec.checkRequest(catalog("limit" to "0")).single().contains("limit"))
        assertTrue(OpenApiSpec.checkRequest(catalog("limit" to "ten")).single().contains("limit"))
        assertEquals(emptyList(), OpenApiSpec.checkRequest(feed("crw_weekend")))
        assertTrue(OpenApiSpec.checkRequest(feed("crw weekend")).single().contains("crewId"))
        assertTrue(OpenApiSpec.checkRequest(feed("crw_weekend").copy(parameters = emptyMap())).single().contains("missing"))
        assertTrue(
            OpenApiSpec.checkRequest(join.copy(parameters = mapOf("idempotency-key" to "again"))).single().contains("Idempotency-Key"),
        )
    }
}
