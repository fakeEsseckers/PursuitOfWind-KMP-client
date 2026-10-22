package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiRoute

/** A route and the path parameters taken from the request path. */
internal data class RouteMatch(
    val route: ApiRoute,
    val pathParameters: Map<String, String>,
)

/** Matches method and path against [ApiRoute]; literal segments must be equal, `{name}` segments take any value. */
internal object Router {
    private val templates = ApiRoute.entries.associateWith { it.path.trim('/').split('/') }

    fun match(
        method: String,
        path: String,
    ): RouteMatch? {
        val segments = path.trim('/').split('/')
        return ApiRoute.entries
            .filter { it.method.equals(method, ignoreCase = true) }
            .firstNotNullOfOrNull { route -> parameters(templates.getValue(route), segments)?.let { RouteMatch(route, it) } }
    }

    private fun parameters(
        template: List<String>,
        segments: List<String>,
    ): Map<String, String>? {
        val pairs = template.zip(segments)
        val matches =
            template.size == segments.size &&
                pairs.all { (expected, actual) -> if (expected.isParameter()) actual.isNotEmpty() else expected == actual }
        return if (matches) pairs.filter { it.first.isParameter() }.associate { (name, value) -> name.trim('{', '}') to value } else null
    }

    private fun String.isParameter(): Boolean = startsWith('{')
}
