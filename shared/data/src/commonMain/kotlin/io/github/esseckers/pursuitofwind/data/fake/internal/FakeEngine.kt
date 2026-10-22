package io.github.esseckers.pursuitofwind.data.fake.internal

import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray

/**
 * The fake server's HTTP engine: hands each request to [handle] and keeps nothing afterwards. Ktor's `MockEngine`
 * records every request and response it serves, which tests like but an app that runs for days does not.
 *
 * Implementing an engine needs Ktor's `InternalAPI` (`execute`, `callContext`, the response constructor), the same
 * surface `MockEngine` uses. A Ktor upgrade may need small changes here; `FakeServerContractTest` and the data layer
 * tests run every request through this class.
 */
@OptIn(InternalAPI::class)
internal class FakeEngine(
    override val config: HttpClientEngineConfig,
    private val handle: suspend (request: HttpRequestData, body: ByteArray) -> FakeResponse,
) : HttpClientEngineBase("pursuitofwind-fake") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val response = withContext(dispatcher + callContext) { handle(data, data.body.bytes()) }
        val headers =
            Headers.build {
                response.contentType?.let { append(HttpHeaders.ContentType, it.toString()) }
                response.headers.forEach { (name, value) -> append(name, value) }
            }
        return HttpResponseData(
            statusCode = response.status,
            requestTime = GMTDate(),
            headers = headers,
            version = HttpProtocolVersion.HTTP_1_1,
            body = ByteReadChannel(response.body.encodeToByteArray()),
            callContext = callContext,
        )
    }
}

/** The bytes a request body would put on the wire. */
private suspend fun OutgoingContent.bytes(): ByteArray =
    when (this) {
        is OutgoingContent.ByteArrayContent -> {
            bytes()
        }

        is OutgoingContent.NoContent -> {
            ByteArray(0)
        }

        is OutgoingContent.ReadChannelContent -> {
            readFrom().readBuffer().readByteArray()
        }

        is OutgoingContent.WriteChannelContent -> {
            coroutineScope {
                val channel = ByteChannel()
                launch {
                    writeTo(channel)
                    channel.flushAndClose()
                }
                channel.readBuffer().readByteArray()
            }
        }

        is OutgoingContent.ContentWrapper -> {
            delegate().bytes()
        }

        is OutgoingContent.ProtocolUpgrade -> {
            error("The fake server does not upgrade protocols")
        }
    }
