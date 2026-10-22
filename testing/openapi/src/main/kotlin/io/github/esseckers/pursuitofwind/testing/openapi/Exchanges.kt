package io.github.esseckers.pursuitofwind.testing.openapi

import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.obj
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A request as sent, with the path template of the operation it targets, e.g. `/v1/rides/{rideId}/seats`, and its
 * path, query and header [parameters] by name.
 */
data class RecordedRequest(
    val method: String,
    val pathTemplate: String,
    val contentType: String?,
    val body: String,
    val parameters: Map<String, String> = emptyMap(),
)

/** A response as received. */
data class RecordedResponse(
    val status: Int,
    val contentType: String?,
    val headerNames: Set<String>,
    val body: String,
)

/** Statuses an operation may return without listing them, through its `default` response. */
private val DEFAULT_STATUSES = setOf(429, 500, 503)

/** Problems with a request's parameters and body; empty when the request conforms to the spec. */
fun OpenApiSpec.checkRequest(request: RecordedRequest): List<String> = checkParameters(request) + checkBody(request)

/** Declared parameters must validate against their schema; required ones must be present. Undeclared ones are ignored. */
private fun OpenApiSpec.checkParameters(request: RecordedRequest): List<String> {
    val where = "${request.method} ${request.pathTemplate}"
    return parameters(request.method, request.pathTemplate).flatMap { parameter ->
        val value =
            request.parameters.entries
                .firstOrNull { it.key.equals(parameter.name, ignoreCase = parameter.location == "header") }
                ?.value
        if (value == null) {
            listOfNotNull("$where is missing ${parameter.location} parameter ${parameter.name}".takeIf { parameter.required })
        } else {
            validate(parameter.schema, parameterJson(parameter.schema, value)).map { "$where ${parameter.location} ${parameter.name}: $it" }
        }
    }
}

/** A parameter value as JSON: numbers and booleans as literals when they parse, everything else as a string. */
private fun OpenApiSpec.parameterJson(
    schema: JsonObject,
    value: String,
): String {
    val literal =
        when (type(schema)) {
            "integer", "number" -> value.takeIf { JSON_NUMBER.matches(it) }
            "boolean" -> value.takeIf { it == "true" || it == "false" }
            else -> null
        }
    return literal ?: JsonPrimitive(value).toString()
}

private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private fun OpenApiSpec.checkBody(request: RecordedRequest): List<String> {
    val where = "${request.method} ${request.pathTemplate}"
    val requestBody = operation(request.method, request.pathTemplate)["requestBody"]?.jsonObject
    val media = requestBody?.obj("content")?.get(mediaType(request.contentType))?.jsonObject
    return when {
        requestBody == null -> listOfNotNull("$where takes no body".takeIf { request.body.isNotEmpty() })
        media == null -> listOf("$where does not accept content type ${request.contentType}")
        else -> validate(refName(media.getValue("schema").jsonObject.getValue("\$ref")), request.body).map { "$where: $it" }
    }
}

/** Problems with a response: undocumented status, content type, missing required headers, invalid body. */
fun OpenApiSpec.checkResponse(
    request: RecordedRequest,
    response: RecordedResponse,
): List<String> {
    val where = "${request.method} ${request.pathTemplate} → ${response.status}"
    val responses = operation(request.method, request.pathTemplate).obj("responses")
    val declared = responses[response.status.toString()] ?: responses["default"]?.takeIf { response.status in DEFAULT_STATUSES }
    return if (declared == null) listOf("$where is not documented") else checkDeclared(where, response(declared), response)
}

private fun checkDeclared(
    where: String,
    declared: JsonObject,
    response: RecordedResponse,
): List<String> {
    val missingHeaders =
        declared["headers"]
            ?.jsonObject
            .orEmpty()
            .filter { (_, header) -> OpenApiSpec.header(header)["required"]?.jsonPrimitive?.content == "true" }
            .keys
            .filterNot { name -> response.headerNames.any { it.equals(name, ignoreCase = true) } }
            .map { "$where is missing header $it" }
    val content = declared["content"]?.jsonObject
    val media = content?.get(mediaType(response.contentType))?.jsonObject
    val bodyProblems =
        when {
            content == null -> {
                listOfNotNull("$where must have no body".takeIf { response.body.isNotEmpty() })
            }

            media == null -> {
                listOf("$where has undocumented content type ${response.contentType}")
            }

            else -> {
                val schema = OpenApiSpec.refName(media.getValue("schema").jsonObject.getValue("\$ref"))
                OpenApiSpec.validate(schema, response.body).map { "$where: $it" }
            }
        }
    return missingHeaders + bodyProblems
}

private fun mediaType(contentType: String?): String = contentType.orEmpty().substringBefore(';').trim()
