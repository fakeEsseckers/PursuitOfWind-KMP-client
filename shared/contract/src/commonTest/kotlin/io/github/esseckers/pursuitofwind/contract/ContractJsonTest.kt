package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

class ContractJsonTest {
    private val profile =
        RiderProfileDto(
            displayName = "Alex",
            weightKg = 80.0,
            level = SkillLevelDto.INTERMEDIATE,
            quiverM2 = listOf(7.0, 9.0, 12.0),
            minSessionMinutes = 120,
            maxDriveMinutes = 180,
            windUnit = WindUnitDto.KNOTS,
        )

    @Test
    fun `absent optional fields are left out instead of sent as null`() {
        val json = ContractJson.encodeToJsonElement(RiderProfileDto.serializer(), profile).jsonObject

        assertFalse("minWindMps" in json)
        assertFalse("maxWindMps" in json)
        assertEquals("INTERMEDIATE", json.getValue("level").jsonPrimitive.content)
        assertEquals(profile, ContractJson.decodeFromJsonElement(RiderProfileDto.serializer(), json))
    }

    @Test
    fun `unknown fields from a newer server are ignored`() {
        val body =
            """
            {"userId":"usr_alex","accessToken":"a","refreshToken":"r","accessTokenExpiresAt":"2026-07-16T07:00:00Z","scope":"x"}
            """.trimIndent()

        val tokens = ContractJson.decodeFromString(AuthTokensDto.serializer(), body)

        assertEquals(Instant.parse("2026-07-16T07:00:00Z"), tokens.accessTokenExpiresAt)
    }

    @Test
    fun `timestamps are written as ISO-8601 UTC`() {
        val crew = CrewDto("crw_weekend", "Weekend Crew", "WIND-4KQ7", 4, Instant.parse("2026-05-02T09:15:00Z"))

        val json = ContractJson.encodeToJsonElement(CrewDto.serializer(), crew).jsonObject

        assertEquals("2026-05-02T09:15:00Z", json.getValue("createdAt").jsonPrimitive.content)
    }

    @Test
    fun `crew events carry their subtype in the type field`() {
        val actor = PersonDto("usr_mira", "Mira")
        val feed =
            CrewFeedDto(
                items =
                    listOf(
                        SeatTakenEventDto("evt_1", "crw_weekend", Instant.parse("2026-07-16T06:12:00Z"), actor, "rid_7f3c", 2, 2),
                        RideCancelledEventDto("evt_2", "crw_weekend", Instant.parse("2026-07-16T07:30:00Z"), actor, "rid_9a10", 3),
                    ),
                hasMore = false,
                lastEventId = "evt_2",
            )

        val json = ContractJson.encodeToJsonElement(CrewFeedDto.serializer(), feed).jsonObject
        val types =
            json.getValue("items").jsonArray.map {
                it.jsonObject
                    .getValue("type")
                    .jsonPrimitive.content
            }
        val decoded = ContractJson.decodeFromJsonElement(CrewFeedDto.serializer(), json)

        assertEquals(listOf("SEAT_TAKEN", "RIDE_CANCELLED"), types)
        assertIs<SeatTakenEventDto>(decoded.items[0])
        assertEquals(feed, decoded)
    }

    @Test
    fun `an empty feed has no last event id`() {
        val json = ContractJson.encodeToJsonElement(CrewFeedDto.serializer(), CrewFeedDto(items = emptyList(), hasMore = false)).jsonObject

        assertEquals(setOf("items", "hasMore"), json.keys)
    }

    @Test
    fun `event types added later decode as unknown events and keep the cursor`() {
        val body =
            """
            {"items":[
              {"id":"evt_9","type":"MEMBER_LEFT","crewId":"crw_weekend","occurredAt":"2026-07-17T10:00:00Z",
               "actor":{"userId":"usr_jonas","displayName":"Jonas"},"member":{"userId":"usr_jonas"}}
            ],"hasMore":false,"lastEventId":"evt_9"}
            """.trimIndent()

        val feed = ContractJson.decodeFromString(CrewFeedDto.serializer(), body)

        val unknown = assertIs<UnknownCrewEventDto>(feed.items.single())
        assertEquals("evt_9", unknown.id)
        assertEquals("evt_9", feed.lastEventId)
    }
}
