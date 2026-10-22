package io.github.esseckers.pursuitofwind.data.network

import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * An engine between a client and [server] that lets a test see every request and decide what happens: pass it on
 * (`forward()`), answer something else, or fail. Used to lose answers the server has already acted on.
 */
internal fun TestScope.forwardingEngine(
    server: FakePursuitOfWindServer,
    intercept: suspend MockRequestHandleScope.(request: HttpRequestData, forward: suspend () -> HttpResponseData) -> HttpResponseData,
): HttpClientEngine = forwardingEngine({ server }, intercept)

/** Like the other overload, but asks [target] for the server on every request, so a test can swap servers. */
internal fun TestScope.forwardingEngine(
    target: () -> FakePursuitOfWindServer,
    intercept: suspend MockRequestHandleScope.(request: HttpRequestData, forward: suspend () -> HttpResponseData) -> HttpResponseData =
        { _, forward -> forward() },
): HttpClientEngine {
    val upstreams = mutableMapOf<FakePursuitOfWindServer, HttpClient>()
    return MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                intercept(request) {
                    val upstream = upstreams.getOrPut(target()) { HttpClient(target().engine) { expectSuccess = false } }
                    val answer =
                        upstream.request(request.url.toString()) {
                            method = request.method
                            request.headers.forEach { name, values -> values.forEach { header(name, it) } }
                            setBody(request.body)
                        }
                    respond(
                        answer.bodyAsText(),
                        answer.status,
                        Headers.build {
                            answer.headers.forEach {
                                name,
                                values,
                                ->
                                appendAll(name, values)
                            }
                        },
                    )
                }
            }
        },
    )
}

/** What a gateway answers when the server's answer got lost. */
internal fun MockRequestHandleScope.answerLost(): HttpResponseData =
    respond(
        """{"type":"/problems/service-unavailable","title":"Lost","status":503,"code":"SERVICE_UNAVAILABLE"}""",
        HttpStatusCode.ServiceUnavailable,
        headersOf("Content-Type", "application/problem+json"),
    )
