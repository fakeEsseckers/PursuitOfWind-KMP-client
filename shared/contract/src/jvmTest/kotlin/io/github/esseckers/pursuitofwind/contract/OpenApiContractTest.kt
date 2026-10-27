package io.github.esseckers.pursuitofwind.contract

import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec
import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.arrayOrEmpty
import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.obj
import io.github.esseckers.pursuitofwind.testing.openapi.Parameter
import io.github.esseckers.pursuitofwind.testing.openapi.parameters
import io.github.esseckers.pursuitofwind.testing.openapi.type
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Keeps `api/openapi.yaml` and the DTOs in this module in agreement:
 * the spec parses, follows the API conventions, every example is valid against its schema and survives a round
 * trip through its DTO, and every schema's fields and required fields match its DTO exactly.
 */
class OpenApiContractTest {
    private val dtoBySchema: Map<String, KSerializer<*>> =
        mapOf(
            "Problem" to ProblemDto.serializer(),
            "Platform" to PlatformDto.serializer(),
            "AnonymousSignInRequest" to AnonymousSignInRequestDto.serializer(),
            "RefreshTokensRequest" to RefreshTokensRequestDto.serializer(),
            "AuthTokens" to AuthTokensDto.serializer(),
            "SkillLevel" to SkillLevelDto.serializer(),
            "WindUnit" to WindUnitDto.serializer(),
            "RiderProfile" to RiderProfileDto.serializer(),
            "GeoPoint" to GeoPointDto.serializer(),
            "Sector" to SectorDto.serializer(),
            "Spot" to SpotDto.serializer(),
            "SpotPage" to SpotPageDto.serializer(),
            "MySpotUpdate" to MySpotUpdateDto.serializer(),
            "MySpot" to MySpotDto.serializer(),
            "MySpotList" to MySpotListDto.serializer(),
            "ThunderRisk" to ThunderRiskDto.serializer(),
            "ModelWind" to ModelWindDto.serializer(),
            "ForecastHour" to ForecastHourDto.serializer(),
            "SpotForecast" to SpotForecastDto.serializer(),
            "Person" to PersonDto.serializer(),
            "CreateCrewRequest" to CreateCrewRequestDto.serializer(),
            "JoinCrewRequest" to JoinCrewRequestDto.serializer(),
            "Crew" to CrewDto.serializer(),
            "CrewList" to CrewListDto.serializer(),
            "MemberRole" to MemberRoleDto.serializer(),
            "CrewMember" to CrewMemberDto.serializer(),
            "CrewMemberList" to CrewMemberListDto.serializer(),
            "RideStatus" to RideStatusDto.serializer(),
            "CreateRideRequest" to CreateRideRequestDto.serializer(),
            "Ride" to RideDto.serializer(),
            "RideList" to RideListDto.serializer(),
            "CrewEvent" to CrewEventDto.serializer(),
            "CrewFeed" to CrewFeedDto.serializer(),
            "PushTokenRequest" to PushTokenRequestDto.serializer(),
        )

    /** Event subtypes travel through the polymorphic [CrewEventDto] serializer, which writes `type`. */
    private val eventSubtypes: Map<String, Pair<KSerializer<*>, KClass<out CrewEventDto>>> =
        mapOf(
            "RideCreatedEvent" to (RideCreatedEventDto.serializer() to RideCreatedEventDto::class),
            "SeatTakenEvent" to (SeatTakenEventDto.serializer() to SeatTakenEventDto::class),
            "SeatReleasedEvent" to (SeatReleasedEventDto.serializer() to SeatReleasedEventDto::class),
            "RideCancelledEvent" to (RideCancelledEventDto.serializer() to RideCancelledEventDto::class),
            "MemberJoinedEvent" to (MemberJoinedEventDto.serializer() to MemberJoinedEventDto::class),
        )

    /** Building blocks that never travel on their own. */
    private val abstractSchemas = setOf("CrewEventBase")

    /** Object schemas without their own example because a larger example covers them. */
    private val coveredByOtherExamples = setOf("RideCancelledEvent", "MemberJoinedEvent", "CrewEvent")

    @Test
    fun `the spec parses without errors or warnings`() {
        assertEquals(emptyList(), OpenApiSpec.parserMessages())
    }

    @Test
    fun `every schema has a DTO with exactly the same fields and required fields`() {
        val unmapped = OpenApiSpec.schemas.keys - dtoBySchema.keys - eventSubtypes.keys - abstractSchemas
        assertEquals(emptySet(), unmapped, "Schemas without a DTO")

        dtoBySchema.forEach { (name, serializer) -> assertSameShape(name, serializer, discriminator = null) }
        eventSubtypes.forEach { (name, subtype) -> assertSameShape(name, subtype.first, discriminator = "type") }
    }

    @Test
    fun `every schema example is valid and survives a round trip through its DTO`() {
        var checked = 0
        OpenApiSpec.schemas.forEach { (name, schema) ->
            val examples = schema.jsonObject["examples"].arrayOrEmpty()
            val isObject = dtoBySchema[name]?.descriptor?.kind == StructureKind.CLASS || name in eventSubtypes
            if (isObject && name !in coveredByOtherExamples) assertTrue(examples.isNotEmpty(), "Schema $name has no example")
            examples.forEach { example ->
                assertValid(name, example)
                assertRoundTrip(name, example)
                checked++
            }
        }
        assertTrue(checked >= dtoBySchema.size / 2, "Only $checked examples checked")
    }

    @Test
    fun `every response example is valid and decodes into its DTO`() {
        val examples = mediaExamples()
        assertTrue(examples.size >= 10, "Expected response examples in the spec, found ${examples.size}")
        examples.forEach { (where, schemaName, example) ->
            assertValid(schemaName, example)
            ContractJson.decodeFromJsonElement(dtoBySchema.getValue(schemaName), example)
        }
    }

    @Test
    fun `errors are problem bodies with a known code and the status of their response`() {
        val knownCodes =
            ProblemCode::class.java.declaredFields
                .filter { it.type == String::class.java }
                .map { it.get(null) as String }
        val problemDescription =
            OpenApiSpec.schemas
                .obj("Problem")
                .getValue("description")
                .jsonPrimitive.content
        val unlisted = knownCodes.filterNot { "`$it`" in problemDescription }
        assertEquals(emptyList(), unlisted, "ProblemCode values missing from the Problem schema description")

        operations().forEach { (where, operation) ->
            operation.obj("responses").forEach { (status, response) ->
                if (status != "default" && !status.startsWith("4")) return@forEach
                val content = OpenApiSpec.response(response).obj("content")
                assertEquals(setOf(ProblemDto.MEDIA_TYPE), content.keys, "$where $status")
                assertEquals(
                    "Problem",
                    OpenApiSpec.refName(
                        content
                            .obj(ProblemDto.MEDIA_TYPE)
                            .getValue("schema")
                            .jsonObject
                            .getValue("\$ref"),
                    ),
                )
            }
        }
        mediaExamples().filter { it.second == "Problem" }.forEach { (where, _, example) ->
            val problem = ContractJson.decodeFromJsonElement(ProblemDto.serializer(), example)
            assertTrue(problem.code in knownCodes, "$where uses unknown code ${problem.code}")
            val status = where.substringAfterLast(' ')
            if (status.all(Char::isDigit)) assertEquals(status.toInt(), problem.status, "$where example status")
        }
    }

    @Test
    fun `examples keep seat counts consistent`() {
        fun check(element: JsonElement) {
            if (element is JsonArray) element.forEach(::check)
            if (element !is JsonObject) return
            // Schema definitions have objects under these keys; only examples have numbers and arrays.
            val total = (element["seatsTotal"] as? JsonPrimitive)?.content?.toIntOrNull()
            val free = (element["seatsFree"] as? JsonPrimitive)?.content?.toIntOrNull()
            val passengers = (element["passengers"] as? JsonArray)?.size
            if (total != null && free != null && passengers != null) {
                assertEquals(total - passengers, free, "Seat math in $element")
            }
            element.values.forEach(::check)
        }
        check(OpenApiSpec.root)
    }

    @Test
    fun `the route table in the contract module matches the spec`() {
        val inSpec = operations().map { (where, operation) -> where to operation.getValue("operationId").jsonPrimitive.content }.toSet()
        val inCode = ApiRoute.entries.map { "${it.method} ${it.path}" to it.operationId }.toSet()

        assertEquals(inSpec, inCode)
        ApiRoute.entries.forEach { route ->
            val operation = operations().toMap().getValue("${route.method} ${route.path}")
            val secured = operation["security"]?.jsonArray?.isNotEmpty() ?: true
            assertEquals(secured, route.requiresAuth, "${route.name} authentication")
        }
    }

    @Test
    fun `operations follow the API conventions`() {
        val operationIds = mutableListOf<String>()
        OpenApiSpec.root.obj("paths").forEach { (path, pathItem) ->
            assertTrue(path.startsWith("/v1/"), "$path is not under /v1")
            HTTP_METHODS.filter { it in pathItem.jsonObject }.forEach { method ->
                val operation = pathItem.jsonObject.obj(method)
                val where = "${method.uppercase()} $path"
                val responses = operation.obj("responses").keys
                val declared = OpenApiSpec.parameters(method, path)
                val parameters = declared.map { it.name }
                val secured = operation["security"]?.jsonArray?.isNotEmpty() ?: true

                operationIds += operation.getValue("operationId").jsonPrimitive.content
                assertTrue(operation["tags"].arrayOrEmpty().isNotEmpty(), "$where has no tag")
                assertTrue("summary" in operation, "$where has no summary")
                assertTrue("default" in responses, "$where has no default response")
                assertTrue(responses.any { it.startsWith("2") }, "$where has no success response")
                if (secured) assertTrue("401" in responses, "$where is authenticated but does not document 401")
                if ("requestBody" in operation) assertTrue("422" in responses, "$where takes a body but does not document 422")
                if (declared.any { it.location == "query" && it.isConstrained() }) {
                    assertTrue("422" in responses, "$where has constrained query parameters but does not document 422")
                }
                if (secured && method != "get") {
                    assertTrue(ApiHeaders.IDEMPOTENCY_KEY in parameters, "$where is a mutation without Idempotency-Key")
                    assertTrue("422" in responses, "$where takes Idempotency-Key but does not document 422 IDEMPOTENCY_KEY_REUSED")
                }
                PATH_PARAMETER.findAll(path).forEach { match ->
                    assertTrue(match.groupValues[1] in parameters, "$where does not declare path parameter ${match.value}")
                }
            }
        }
        assertEquals(operationIds.size, operationIds.toSet().size, "Duplicate operationId in $operationIds")
    }

    /** Sealed descriptors are still experimental in kotlinx.serialization; reading them in a test is fine. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun assertSameShape(
        name: String,
        serializer: KSerializer<*>,
        discriminator: String?,
    ) {
        val descriptor = serializer.descriptor
        when (descriptor.kind) {
            SerialKind.ENUM -> {
                val values =
                    OpenApiSpec.schemas
                        .obj(name)
                        .getValue("enum")
                        .jsonArray
                        .map { it.jsonPrimitive.content }
                assertEquals(values, descriptor.elementNames.toList(), "Enum $name")
            }

            StructureKind.CLASS -> {
                val (properties, required) = OpenApiSpec.objectShape(name)
                val dtoRequired =
                    (0 until descriptor.elementsCount)
                        .filterNot(descriptor::isElementOptional)
                        .map(descriptor::getElementName)
                assertEquals(properties.keys - setOfNotNull(discriminator), descriptor.elementNames.toSet(), "Fields of $name")
                assertEquals(required - setOfNotNull(discriminator), dtoRequired.toSet(), "Required fields of $name")
                (0 until descriptor.elementsCount).forEach { index ->
                    val field = descriptor.getElementName(index)
                    assertSameType("$name.$field", properties.getValue(field), descriptor.getElementDescriptor(index))
                }
            }

            PolymorphicKind.SEALED -> {
                val variants =
                    OpenApiSpec.schemas
                        .obj(name)
                        .getValue("oneOf")
                        .jsonArray
                        .map { ref(it) }
                assertEquals(eventSubtypes.keys, variants.toSet(), "Variants of $name")
            }

            else -> {
                fail("Unexpected descriptor kind ${descriptor.kind} for $name")
            }
        }
    }

    /** Schema type of a property against the Kotlin type of the DTO field. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun assertSameType(
        where: String,
        schema: JsonObject,
        field: SerialDescriptor,
    ) {
        val ref = schema["\$ref"]
        val type = schema["type"]?.jsonPrimitive?.content
        when {
            ref != null -> {
                val expected = dtoBySchema.getValue(OpenApiSpec.refName(ref)).descriptor.serialName
                assertEquals(expected, field.serialName.removeSuffix("?"), where)
            }

            type == "array" -> {
                assertEquals(StructureKind.LIST, field.kind, where)
                assertSameType("$where[]", schema.obj("items"), field.getElementDescriptor(0))
            }

            type == "string" && schema["format"]?.jsonPrimitive?.content == "date-time" -> {
                assertEquals("kotlin.time.Instant", field.serialName.removeSuffix("?"), where)
            }

            else -> {
                assertEquals<SerialKind?>(PRIMITIVE_KINDS[type], field.kind, where)
            }
        }
    }

    /** `("POST /v1/crews", operation)` for every operation in the spec. */
    private fun operations(): List<Pair<String, JsonObject>> =
        OpenApiSpec.root.obj("paths").flatMap { (path, pathItem) ->
            HTTP_METHODS.filter { it in pathItem.jsonObject }.map { method ->
                "${method.uppercase()} $path" to
                    pathItem.jsonObject.obj(method)
            }
        }

    private fun assertValid(
        schemaName: String,
        example: JsonElement,
    ) {
        assertEquals(emptyList(), OpenApiSpec.validate(schemaName, example.toString()), "Example of $schemaName: $example")
    }

    private fun assertRoundTrip(
        schemaName: String,
        example: JsonElement,
    ) {
        val subtype = eventSubtypes[schemaName]

        @Suppress("UNCHECKED_CAST")
        val serializer = (if (subtype != null) CrewEventDto.serializer() else dtoBySchema.getValue(schemaName)) as KSerializer<Any>

        val decoded = ContractJson.decodeFromJsonElement(serializer, example)
        val encoded = ContractJson.encodeToJsonElement(serializer, decoded)

        subtype?.let { assertTrue(it.second.isInstance(decoded), "$schemaName decoded as ${decoded::class.simpleName}") }
        assertTrue(sameJson(example, encoded), "$schemaName example changed in a round trip:\n$example\n$encoded")
    }

    /** `(location ending in the status, schema name, example)` for every `example` and `examples` entry of a response. */
    private fun mediaExamples(): List<Triple<String, String, JsonElement>> {
        val responses =
            operations().flatMap { (where, operation) ->
                operation.obj("responses").map { (status, response) -> "$where $status" to OpenApiSpec.response(response) }
            } +
                OpenApiSpec.root
                    .obj("components")
                    .obj("responses")
                    .map { (name, response) -> "responses/$name" to response.jsonObject }

        return responses.flatMap { (where, response) ->
            response["content"]?.jsonObject.orEmpty().flatMap { (_, media) ->
                val schema = media.jsonObject["schema"]?.let { ref(it) } ?: return@flatMap emptyList()
                val single = listOfNotNull(media.jsonObject["example"])
                val named =
                    media.jsonObject["examples"]
                        ?.jsonObject
                        ?.values
                        ?.map { it.jsonObject.getValue("value") }
                        .orEmpty()
                (single + named).map { Triple(where, schema, it) }
            }
        }
    }

    private fun ref(schema: JsonElement): String =
        schema.jsonObject
            .getValue("\$ref")
            .jsonPrimitive.content
            .substringAfterLast('/')

    /** A value the server has to check, so a bad one is `422`: a non-string type or any keyword that limits strings. */
    private fun Parameter.isConstrained(): Boolean = OpenApiSpec.type(schema) != "string" || schema.keys.any { it in STRING_LIMITS }

    /** JSON equality where `80` and `80.0` are the same number. */
    private fun sameJson(
        a: JsonElement,
        b: JsonElement,
    ): Boolean =
        when {
            a is JsonObject && b is JsonObject -> {
                a.keys == b.keys && a.keys.all { sameJson(a.getValue(it), b.getValue(it)) }
            }

            a is JsonArray && b is JsonArray -> {
                a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
            }

            a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString && a.doubleOrNull != null -> {
                a.doubleOrNull == b.doubleOrNull
            }

            else -> {
                a == b
            }
        }

    private companion object {
        val HTTP_METHODS = listOf("get", "put", "post", "delete", "patch")
        val PATH_PARAMETER = Regex("\\{([^}]+)}")
        val STRING_LIMITS = setOf("format", "pattern", "enum", "minLength", "maxLength")
        val PRIMITIVE_KINDS =
            mapOf(
                "string" to PrimitiveKind.STRING,
                "integer" to PrimitiveKind.INT,
                "number" to PrimitiveKind.DOUBLE,
                "boolean" to PrimitiveKind.BOOLEAN,
            )
    }
}
