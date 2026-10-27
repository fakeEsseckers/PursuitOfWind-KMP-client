package io.github.esseckers.pursuitofwind.contract

import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec
import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.obj
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** [ApiLimits] are the limits `api/openapi.yaml` states, field by field. */
class ApiLimitsContractTest {
    @Test
    fun `the rider profile`() {
        assertEquals(ApiLimits.RiderProfile.DISPLAY_NAME_MAX_LENGTH.toDouble(), field("RiderProfile", "displayName", "maxLength"))
        assertEquals(ApiLimits.RiderProfile.WEIGHT_MIN_KG, field("RiderProfile", "weightKg", "minimum"))
        assertEquals(ApiLimits.RiderProfile.WEIGHT_MAX_KG, field("RiderProfile", "weightKg", "maximum"))
        assertEquals(ApiLimits.RiderProfile.QUIVER_MAX_KITES.toDouble(), field("RiderProfile", "quiverM2", "maxItems"))
        assertEquals(ApiLimits.RiderProfile.KITE_MAX_M2, property("RiderProfile", "quiverM2").obj("items").limit("maximum"))
        assertEquals(ApiLimits.RiderProfile.MIN_SESSION_MIN_MINUTES.toDouble(), field("RiderProfile", "minSessionMinutes", "minimum"))
        assertEquals(ApiLimits.RiderProfile.MIN_SESSION_MAX_MINUTES.toDouble(), field("RiderProfile", "minSessionMinutes", "maximum"))
        assertEquals(0.0, field("RiderProfile", "maxDriveMinutes", "minimum"))
        assertEquals(ApiLimits.RiderProfile.MAX_DRIVE_MAX_MINUTES.toDouble(), field("RiderProfile", "maxDriveMinutes", "maximum"))
    }

    @Test
    fun `the name of a spot a rider adds`() {
        listOf("CreateSpotRequest", "SpotUpdate").forEach { schema ->
            assertEquals(ApiLimits.Spot.NAME_MAX_LENGTH.toDouble(), field(schema, "name", "maxLength"), schema)
        }
    }

    @Test
    fun `the drive to a followed spot`() {
        listOf("MySpot", "MySpotUpdate").forEach { schema ->
            assertEquals(0.0, field(schema, "driveMinutes", "minimum"), schema)
            assertEquals(ApiLimits.MySpot.DRIVE_MAX_MINUTES.toDouble(), field(schema, "driveMinutes", "maximum"), schema)
        }
    }

    @Test
    fun `a crew's name`() {
        assertEquals(ApiLimits.Crew.NAME_MAX_LENGTH.toDouble(), field("CreateCrewRequest", "name", "maxLength"))
    }

    @Test
    fun `a ride's meeting point and seats`() {
        assertEquals(ApiLimits.Ride.MEETING_POINT_MAX_LENGTH.toDouble(), field("CreateRideRequest", "meetingPoint", "maxLength"))
        listOf("CreateRideRequest", "Ride").forEach { schema ->
            assertEquals(ApiLimits.Ride.SEATS_MIN.toDouble(), field(schema, "seatsTotal", "minimum"), schema)
            assertEquals(ApiLimits.Ride.SEATS_MAX.toDouble(), field(schema, "seatsTotal", "maximum"), schema)
        }
        assertEquals(ApiLimits.Ride.SEATS_MAX.toDouble(), field("Ride", "seatsFree", "maximum"))
    }

    @Test
    fun `pages and forecast hours`() {
        val limit =
            OpenApiSpec.root
                .obj("components")
                .obj("parameters")
                .obj("Limit")
                .obj("schema")
        val hours =
            OpenApiSpec
                .operation("GET", "/v1/spots/{spotId}/forecast")
                .getValue("parameters")
                .jsonArray
                .map { it.jsonObject }
                .single { it["name"]?.jsonPrimitive?.content == "hours" }
                .obj("schema")

        assertEquals(ApiLimits.Paging.LIMIT_MAX.toDouble(), limit.limit("maximum"))
        assertEquals(ApiLimits.Forecast.HOURS_MAX.toDouble(), hours.limit("maximum"))
    }

    private fun property(
        schema: String,
        name: String,
    ): JsonObject =
        OpenApiSpec.schemas
            .obj(schema)
            .obj("properties")
            .obj(name)

    private fun field(
        schema: String,
        name: String,
        keyword: String,
    ): Double = property(schema, name).limit(keyword)

    private fun JsonObject.limit(keyword: String): Double = requireNotNull(this[keyword]) { "Missing '$keyword'" }.jsonPrimitive.double
}
