package io.github.esseckers.pursuitofwind.data.network

import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.data.auth.InMemorySecureTokenStore
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.Seed
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.internal.network.AuthSession
import io.github.esseckers.pursuitofwind.data.internal.network.HttpClients
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.RetryPolicy
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.encodedPath
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** A fake server on the test scheduler, so its delays and the client's retry back-off run in virtual time. */
internal fun TestScope.fakeServer(
    clock: ControllableClock = ControllableClock(THURSDAY_MORNING),
    seed: Seed = Seed.default(clock.now()),
): FakePursuitOfWindServer = FakePursuitOfWindServer(clock, seed, StandardTestDispatcher(testScheduler))

/**
 * The network half of the data layer as the app wires it: one device (its own token store) talking to [engine].
 * [authPaths] records every call of the auth client, [apiSends] every attempt of the API client including retries.
 */
internal class NetworkHarness(
    engine: HttpClientEngine,
    baseUrl: String = FakePursuitOfWindServer.BASE_URL,
    onSignedIn: suspend (userId: String, newUser: Boolean) -> Unit = { _, _ -> },
) {
    val tokens = InMemorySecureTokenStore()
    val authPaths = mutableListOf<String>()
    val apiSends = mutableListOf<String>()
    val clients: HttpClients = HttpClients(engine, baseUrl, RetryPolicy.Deterministic) { session }
    val session = AuthSession({ clients.auth }, tokens, PlatformDto.ANDROID, onSignedIn)
    val api = PursuitOfWindApi(clients.api)

    init {
        clients.auth.plugin(HttpSend).intercept { request ->
            authPaths += request.url.encodedPath
            execute(request)
        }
        clients.api.plugin(HttpSend).intercept { request ->
            apiSends += "${request.method.value} ${request.url.encodedPath}"
            execute(request)
        }
    }
}
