package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProblemDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a problem body from the API`() {
        val body =
            """
            {
              "type": "/problems/seats-full",
              "title": "No seats left",
              "status": 409,
              "code": "SEATS_FULL",
              "detail": "Ride 7f3c has no free seats.",
              "instance": "/v1/rides/7f3c/seats"
            }
            """.trimIndent()

        val problem = json.decodeFromString<ProblemDto>(body)

        assertEquals(409, problem.status)
        assertEquals("SEATS_FULL", problem.code)
        assertEquals("Ride 7f3c has no free seats.", problem.detail)
        assertEquals("/v1/rides/7f3c/seats", problem.instance)
    }

    @Test
    fun `optional fields survive a round trip`() {
        val problem = ProblemDto(type = "/problems/unauthorized", title = "Unauthorized", status = 401, code = "UNAUTHORIZED")

        val decoded = json.decodeFromString<ProblemDto>(json.encodeToString(ProblemDto.serializer(), problem))

        assertEquals(problem, decoded)
        assertNull(decoded.detail)
        assertNull(decoded.instance)
    }
}
