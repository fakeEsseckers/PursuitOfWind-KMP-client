package io.github.esseckers.pursuitofwind.app

import io.github.esseckers.pursuitofwind.core.platform.NoPushToken
import io.github.esseckers.pursuitofwind.core.platform.PushTokenSource
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.testing.HeldRequests
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The server learns the device's push token at the start of the app, on the app's own graph and the fake backend. */
class PushTokenRegistrarTest {
    private val clock = ControllableClock(LocalDate(2026, 10, 22).atTime(LocalTime(8, 0)).toInstant(TimeZone.UTC))
    private val registrations = HeldRequests { it.method == HttpMethod.Put && it.url.encodedPath == "/v1/me/push-token" }
    private var token: String? = "fcm-token"
    private val koin =
        testGraph(
            clock,
            module {
                single<HttpClientEngine> { registrations.engine(get<FakePursuitOfWindServer>()) }
                single<PushTokenSource> {
                    object : PushTokenSource {
                        override suspend fun token(): String? = token
                    }
                }
            },
        ).koin

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    @Test
    fun `a device with a push token registers it with the server`() =
        runTest {
            koin.get<PushTokenRegistrar>().register()

            assertEquals(1, registrations.sent)
        }

    @Test
    fun `a device without a push service asks the server for nothing`() =
        runTest {
            token = null

            koin.get<PushTokenRegistrar>().register()
            PushTokenRegistrar(NoPushToken, koin.get()).register()

            assertEquals(0, registrations.sent)
        }

    @Test
    fun `a registration that fails is no reason to stop the app`() =
        runTest {
            koin.get<FakePursuitOfWindServer>().networkMode = NetworkMode.OFFLINE

            koin.get<PushTokenRegistrar>().register()
        }
}
