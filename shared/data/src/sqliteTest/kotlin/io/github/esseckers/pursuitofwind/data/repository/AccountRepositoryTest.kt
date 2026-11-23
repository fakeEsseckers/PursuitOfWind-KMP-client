package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.network.forwardingEngine
import io.ktor.http.encodedPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The anonymous account of an installation, and the address the server may push to. */
class AccountRepositoryTest {
    @Test
    fun `the device's push token is sent to the server and may be sent again`() =
        runTest {
            world {
                val sent = mutableListOf<String>()
                val alex =
                    device(
                        engine =
                            forwardingEngine(server) { request, forward ->
                                if (request.url.encodedPath == ApiRoute.PUT_PUSH_TOKEN.path) sent += request.method.value
                                forward()
                            },
                    )

                alex.account.registerPushToken("fcm-token-1").value()
                alex.account.registerPushToken("fcm-token-2").value()

                assertEquals(listOf("PUT", "PUT"), sent)
            }
        }

    @Test
    fun `offline the push token is not registered and the failure says why`() =
        runTest {
            world {
                val alex = device()
                alex.account.signIn().value()
                server.networkMode = NetworkMode.OFFLINE

                assertEquals(AppError.Network, alex.account.registerPushToken("fcm-token").errorOrNull())
            }
        }
}
