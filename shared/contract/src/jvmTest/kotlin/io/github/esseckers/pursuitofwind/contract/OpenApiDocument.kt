package io.github.esseckers.pursuitofwind.contract

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
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
 * `api/openapi.yaml` (on the test classpath, see the module's build file) as a JSON tree, plus validation of JSON
 * payloads against its component schemas.
 */
internal object OpenApiDocument {
    private const val RESOURCE = "openapi.yaml"

    val text: String =
        requireNotNull(OpenApiDocument::class.java.classLoader.getResource(RESOURCE)) { "$RESOURCE is not on the test classpath" }
            .readText()

    val root: JsonObject = Json.parseToJsonElement(JsonMapper().writeValueAsString(YAMLMapper().readTree(text))).jsonObject

    val schemas: JsonObject = root.obj("components").obj("schemas")

    private val registry = SchemaRegistry.withDialect(Dialects.getOpenApi31())

    /** Validation errors of [json] against `#/components/schemas/[schemaName]`, with format assertions on. */
    fun validate(
        schemaName: String,
        json: String,
    ): List<String> {
        val schema = registry.getSchema(SchemaLocation.of("classpath:$RESOURCE#/components/schemas/$schemaName"))
        return schema
            .validate(json, InputFormat.JSON) { context -> context.executionConfig { it.formatAssertionsEnabled(true) } }
            .map { it.toString() }
    }

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

    fun JsonObject.obj(key: String): JsonObject = requireNotNull(this[key]) { "Missing '$key'" }.jsonObject

    fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray).orEmpty()
}
