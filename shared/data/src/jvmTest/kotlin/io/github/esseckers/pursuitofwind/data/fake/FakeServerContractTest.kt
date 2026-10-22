package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec
import io.github.esseckers.pursuitofwind.testing.openapi.OpenApiSpec.obj
import io.github.esseckers.pursuitofwind.testing.openapi.RecordedRequest
import io.github.esseckers.pursuitofwind.testing.openapi.RecordedResponse
import io.github.esseckers.pursuitofwind.testing.openapi.checkRequest
import io.github.esseckers.pursuitofwind.testing.openapi.checkResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates every exchange recorded during the tour against `api/openapi.yaml`: request parameters and bodies,
 * response statuses, required headers and bodies. If the fake and the spec disagree, the fake is wrong (contract
 * first).
 */
class FakeServerContractTest {
    @Test
    fun `every exchange of the tour conforms to the spec`() =
        runTest {
            val driver = fakeDriver()
            driver.tourEveryRoute()
            driver.recordFlakyFailure()

            val problems = driver.exchanges.flatMap(::problemsOf)

            assertEquals(emptyList(), problems, problems.joinToString("\n"))
        }

    @Test
    fun `the tour exercises every documented status of every operation`() =
        runTest {
            val driver = fakeDriver()
            driver.tourEveryRoute()

            val seen = driver.exchanges.map { it.route to it.status.toString() }.toSet()
            val missing =
                ApiRoute.entries.flatMap { route ->
                    OpenApiSpec
                        .operation(route.method, route.path)
                        .obj("responses")
                        .keys
                        .filter { it != "default" && (route to it) !in seen }
                        .map { "${route.method} ${route.path} → $it" }
                }

            assertEquals(emptyList(), missing, "Not exercised by the tour:\n" + missing.joinToString("\n"))
        }

    @Test
    fun `the tour records every type of crew event`() =
        runTest {
            val driver = fakeDriver()
            driver.tourEveryRoute()

            val recorded =
                driver.exchanges
                    .filter { it.route == ApiRoute.GET_CREW_FEED && it.status == 200 }
                    .flatMap {
                        Json
                            .parseToJsonElement(it.body)
                            .jsonObject
                            .getValue("items")
                            .jsonArray
                    }.map {
                        it.jsonObject
                            .getValue("type")
                            .jsonPrimitive.content
                    }.toSet()
            val documented =
                OpenApiSpec.schemas
                    .obj("CrewEvent")
                    .obj("discriminator")
                    .obj("mapping")
                    .keys

            assertEquals(documented, recorded)
        }

    private suspend fun FakeApiDriver.recordFlakyFailure() {
        val token = signIn()
        server.networkMode = NetworkMode.FLAKY
        val failed = (1..MAX_ATTEMPTS).any { call(ApiRoute.LIST_MY_SPOTS, token = token).status == SERVICE_UNAVAILABLE }
        server.networkMode = NetworkMode.NORMAL
        check(failed) { "FLAKY did not fail once in $MAX_ATTEMPTS requests" }
    }

    /**
     * Response problems, plus request problems when the fake accepted a request the spec rejects. Such a request must
     * get `422`, or `401` on an authenticated route, where authentication comes first.
     */
    private fun problemsOf(exchange: FakeExchange): List<String> {
        val request =
            RecordedRequest(
                method = exchange.route.method,
                pathTemplate = exchange.route.path,
                contentType = if (exchange.requestBody.isEmpty()) null else JSON,
                body = exchange.requestBody,
                parameters = exchange.parameters,
            )
        val response =
            RecordedResponse(
                status = exchange.status,
                contentType = exchange.contentType,
                headerNames = exchange.headers.names(),
                body = exchange.body,
            )
        val rejected = exchange.status == UNPROCESSABLE || (exchange.status == UNAUTHORIZED && exchange.route.requiresAuth)
        val requestProblems = OpenApiSpec.checkRequest(request).takeUnless { rejected }.orEmpty()
        return requestProblems.map { "accepted with ${exchange.status}: $it" } + OpenApiSpec.checkResponse(request, response)
    }

    private companion object {
        const val JSON = "application/json"
        const val UNAUTHORIZED = 401
        const val UNPROCESSABLE = 422
        const val SERVICE_UNAVAILABLE = 503
        const val MAX_ATTEMPTS = 100
    }
}
