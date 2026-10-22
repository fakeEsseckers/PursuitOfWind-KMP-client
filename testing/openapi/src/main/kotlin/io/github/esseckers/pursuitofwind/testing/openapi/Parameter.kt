package io.github.esseckers.pursuitofwind.testing.openapi

import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.arrayOrEmpty
import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.obj
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A parameter an operation declares; [location] is `path`, `query` or `header`. */
data class Parameter(
    val name: String,
    val location: String,
    val required: Boolean,
    val schema: JsonObject,
)

/** The `type` of a schema object, following a `$ref` into `components/schemas`. */
fun OpenApiSpec.type(schema: JsonObject): String? =
    (schema["\$ref"]?.let { schemas.obj(refName(it)) } ?: schema)["type"]?.jsonPrimitive?.content

/** Path-level and operation-level parameters of an operation, with `$ref`s followed. */
fun OpenApiSpec.parameters(
    method: String,
    pathTemplate: String,
): List<Parameter> {
    val pathItem = root.obj("paths").obj(pathTemplate)
    return (pathItem["parameters"].arrayOrEmpty() + pathItem.obj(method.lowercase())["parameters"].arrayOrEmpty()).map { element ->
        val parameter =
            element.jsonObject["\$ref"]?.let { root.obj("components").obj("parameters").obj(refName(it)) } ?: element.jsonObject
        Parameter(
            name = parameter.getValue("name").jsonPrimitive.content,
            location = parameter.getValue("in").jsonPrimitive.content,
            required = parameter["required"]?.jsonPrimitive?.booleanOrNull == true,
            schema = parameter.obj("schema"),
        )
    }
}
