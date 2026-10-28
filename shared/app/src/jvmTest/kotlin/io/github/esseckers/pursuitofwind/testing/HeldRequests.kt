package io.github.esseckers.pursuitofwind.testing

import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sits between the app and the fake server and passes every request on. It counts the requests [matching], and a test
 * can [hold] the next one until it calls [release], to act while that request is in flight. Bind [engine] in the test
 * graph in place of the server's own engine; to watch two kinds of request, put one in front of the other's engine.
 */
internal class HeldRequests(
    private val matching: (HttpRequestData) -> Boolean,
) {
    private val count = AtomicInteger()

    @Volatile
    private var held: Hold? = null

    /** How many requests [matching] were sent so far. */
    val sent: Int get() = count.get()

    /** Holds the next request [matching]; [awaitHeld] returns once it arrived. */
    fun hold() {
        held = Hold()
    }

    /** Waits, in real time, until the request asked for by [hold] arrived. */
    suspend fun awaitHeld(timeout: Duration = 10.seconds) {
        val hold = checkNotNull(held) { "Nothing is held" }
        withContext(Dispatchers.Default) { withTimeout(timeout) { hold.arrived.await() } }
    }

    /** Lets the held request go on to the server. */
    fun release() {
        held?.released?.complete(Unit)
        held = null
    }

    fun engine(server: FakePursuitOfWindServer): HttpClientEngine = engine(server.engine)

    /** Passes requests on to [next]: the server's engine, or another [HeldRequests]' one. */
    fun engine(next: HttpClientEngine): HttpClientEngine {
        val upstream = HttpClient(next) { expectSuccess = false }
        return MockEngine { request ->
            if (matching(request)) {
                count.incrementAndGet()
                held?.takeIf { it.arrived.complete(Unit) }?.released?.await()
            }
            val answer =
                upstream.request(request.url.toString()) {
                    method = request.method
                    request.headers.forEach { name, values -> values.forEach { header(name, it) } }
                    setBody(request.body)
                }
            val headers = Headers.build { answer.headers.forEach { name, values -> appendAll(name, values) } }
            respond(answer.bodyAsText(), answer.status, headers)
        }
    }

    private class Hold {
        val arrived = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
    }
}
