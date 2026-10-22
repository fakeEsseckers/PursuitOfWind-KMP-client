package io.github.esseckers.pursuitofwind.testing.openapi

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper

/**
 * `api/openapi.yaml` (on this module's classpath) as a JSON tree, and validation of JSON payloads against its schemas
 * with the OpenAPI 3.1 dialect and format assertions on. Requests and responses are checked in `Exchanges.kt`.
 */
object OpenApiSpec {
    private const val RESOURCE = "openapi.yaml"

    val text: String =
        requireNotNull(OpenApiSpec::class.java.classLoader.getResource(RESOURCE)) { "$RESOURCE is not on the classpath" }.readText()

    val root: JsonObject = Json.parseToJsonElement(JsonMapper().writeValueAsString(YAMLMapper().readTree(text))).jsonObject

    val schemas: JsonObject = root.obj("components").obj("schemas")

    private val registry = SchemaRegistry.withDialect(Dialects.getOpenApi31())

    /** Errors and warnings swagger-parser reports for the whole document. */
    fun parserMessages(): List<String> {
        val options =
            ParseOptions().apply {
                isResolve = true
                isValidateInternalRefs = true
            }
        val result = OpenAPIV3Parser().readContents(text, null, options)
        return result.messages.orEmpty() +
            listOfNotNull("Version is ${result.openAPI?.openapi}".takeIf { result.openAPI?.openapi != "3.1.0" })
    }

    /** Validation errors of [json] against `#/components/schemas/[schemaName]`. */
    fun validate(
        schemaName: String,
        json: String,
    ): List<String> = validate(registry.getSchema(SchemaLocation.of("classpath:$RESOURCE#/components/schemas/$schemaName")), json)

    /** Validation errors of [json] against a schema object; a `$ref` is looked up in `components/schemas`. */
    fun validate(
        schema: JsonObject,
        json: String,
    ): List<String> {
        val ref = schema["\$ref"] ?: return validate(registry.getSchema(schema.toString(), InputFormat.JSON), json)
        return validate(refName(ref), json)
    }

    /** The operation for an HTTP [method] and a path template such as `/v1/rides/{rideId}/seats`. */
    fun operation(
        method: String,
        pathTemplate: String,
    ): JsonObject = root.obj("paths").obj(pathTemplate).obj(method.lowercase())

    /** Properties (name to schema; later `allOf` parts win) and required names of an object schema. */
    fun objectShape(schemaName: String): Pair<Map<String, JsonObject>, Set<String>> {
        val properties = mutableMapOf<String, JsonObject>()
        val required = mutableSetOf<String>()

        fun collect(schema: JsonObject) {
            schema["\$ref"]?.let { collect(schemas.obj(refName(it))) }
            schema["allOf"]?.jsonArray?.forEach { collect(it.jsonObject) }
            schema["properties"]?.jsonObject?.forEach { (name, property) -> properties[name] = property.jsonObject }
            schema["required"]?.jsonArray?.forEach { required += it.jsonPrimitive.content }
        }
        collect(schemas.obj(schemaName))
        return properties to required
    }

    /** `#/components/schemas/Ride` → `Ride`. */
    fun refName(ref: JsonElement): String = ref.jsonPrimitive.content.substringAfterLast('/')

    /** A response object, following a `$ref` into `components/responses`. */
    fun response(response: JsonElement): JsonObject =
        response.jsonObject["\$ref"]?.let { root.obj("components").obj("responses").obj(refName(it)) } ?: response.jsonObject

    /** A header object, following a `$ref` into `components/headers`. */
    fun header(header: JsonElement): JsonObject =
        header.jsonObject["\$ref"]?.let { root.obj("components").obj("headers").obj(refName(it)) } ?: header.jsonObject

    fun JsonObject.obj(key: String): JsonObject = requireNotNull(this[key]) { "Missing '$key'" }.jsonObject

    fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray).orEmpty()
}

private fun validate(
    schema: Schema,
    json: String,
): List<String> =
    schema
        .validate(json, InputFormat.JSON) { context -> context.executionConfig { it.formatAssertionsEnabled(true) } }
        .map { it.toString() }
