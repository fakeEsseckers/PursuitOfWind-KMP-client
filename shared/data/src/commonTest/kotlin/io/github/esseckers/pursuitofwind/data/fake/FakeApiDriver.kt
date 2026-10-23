package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.AnonymousSignInRequestDto
import io.github.esseckers.pursuitofwind.contract.ApiHeaders
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.contract.ProblemDto
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Thursday 08:00 in Berlin, the tests' "now". */
val THURSDAY_MORNING: Instant = Instant.parse("2026-07-16T06:00:00Z")

/**
 * One request and its response, kept so the JVM contract test can validate all of them against the spec.
 * [parameters] holds the path, query and header parameters the request carried, by name.
 */
data class FakeExchange(
    val route: ApiRoute,
    val parameters: Map<String, String>,
    val requestBody: String,
    val status: Int,
    val contentType: String?,
    val headers: Headers,
    val body: String,
) {
    fun <T> decode(serializer: KSerializer<T>): T = ContractJson.decodeFromString(serializer, body)

    fun problem(): ProblemDto = decode(ProblemDto.serializer())

    override fun toString(): String = "${route.method} ${route.path} → $status $body"
}

/** Talks to a [FakePursuitOfWindServer] through a real Ktor client, the way the app will. */
class FakeApiDriver(
    val server: FakePursuitOfWindServer,
) {
    private val client = HttpClient(server.engine) { expectSuccess = false }
    val exchanges = mutableListOf<FakeExchange>()

    /** A GET with query parameters. */
    suspend fun get(
        route: ApiRoute,
        vararg pathParameters: String,
        token: String?,
        query: Map<String, String>,
    ): FakeExchange = exchange(Request(route, pathParameters.toList(), token, query = query))

    /** Any request; [body] is sent as JSON exactly as given, so it can be malformed on purpose. */
    suspend fun call(
        route: ApiRoute,
        vararg pathParameters: String,
        token: String? = null,
        body: String? = null,
        idempotencyKey: String? = null,
    ): FakeExchange = exchange(Request(route, pathParameters.toList(), token, body = body, idempotencyKey = idempotencyKey))

    /** A request with [value] encoded as its JSON body. */
    suspend inline fun <reified T> send(
        route: ApiRoute,
        vararg pathParameters: String,
        token: String?,
        value: T,
        idempotencyKey: String? = null,
    ): FakeExchange =
        call(route, *pathParameters, token = token, body = ContractJson.encodeToString(value), idempotencyKey = idempotencyKey)

    private data class Request(
        val route: ApiRoute,
        val pathParameters: List<String>,
        val token: String?,
        val query: Map<String, String> = emptyMap(),
        val body: String? = null,
        val idempotencyKey: String? = null,
    ) {
        val parameters: Map<String, String> =
            route.pathParameters.zip(pathParameters).toMap() + query +
                listOfNotNull(idempotencyKey?.let { ApiHeaders.IDEMPOTENCY_KEY to it })
    }

    private suspend fun exchange(request: Request): FakeExchange {
        val response =
            client.request(FakePursuitOfWindServer.BASE_URL + request.route.pathWith(*request.pathParameters.toTypedArray())) {
                method = HttpMethod.parse(request.route.method)
                request.query.forEach { (name, value) -> parameter(name, value) }
                request.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                request.idempotencyKey?.let { header(ApiHeaders.IDEMPOTENCY_KEY, it) }
                request.body?.let { setBody(TextContent(it, ContentType.Application.Json)) }
            }
        return FakeExchange(
            route = request.route,
            parameters = request.parameters,
            requestBody = request.body.orEmpty(),
            status = response.status.value,
            contentType = response.headers[HttpHeaders.ContentType],
            headers = response.headers,
            body = response.bodyAsText(),
        ).also { exchanges += it }
    }

    /** Signs in as the installation with the given number and returns its access token. */
    suspend fun signIn(installation: Int = 1): String {
        val request = AnonymousSignInRequestDto(installationId(installation), PlatformDto.ANDROID)
        val exchange =
            send(ApiRoute.SIGN_IN_ANONYMOUSLY, token = null, value = request)
        assertEquals(true, exchange.status in 200..201, "sign-in: $exchange")
        return exchange.decode(AuthTokensDto.serializer()).accessToken
    }

    companion object {
        fun installationId(number: Int): String = "00000000-0000-4000-8000-" + number.toString().padStart(12, '0')

        fun key(number: Int): String = "11111111-2222-4333-8444-" + number.toString().padStart(12, '0')
    }
}

/** A driver on a fresh fake server whose engine runs on the test scheduler, so delays are virtual. */
fun TestScope.fakeDriver(
    clock: ControllableClock = ControllableClock(THURSDAY_MORNING),
    seed: Seed = Seed.default(clock.now()),
): FakeApiDriver = FakeApiDriver(FakePursuitOfWindServer(clock, seed, StandardTestDispatcher(testScheduler)))
