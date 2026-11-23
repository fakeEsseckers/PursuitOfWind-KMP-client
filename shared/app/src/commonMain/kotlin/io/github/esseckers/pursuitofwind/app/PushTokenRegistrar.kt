package io.github.esseckers.pursuitofwind.app

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.core.platform.PushTokenSource
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository

/**
 * Gives the server this device's push token when the app starts, so the server can tell the rider about a new window
 * or a ride. With no push service on the device ([PushTokenSource.token] is null) it does nothing, which is every
 * device in the client phase. A failure is logged and tried again at the next start: pushes are a convenience, never a
 * reason to stop the app.
 */
internal class PushTokenRegistrar(
    private val source: PushTokenSource,
    private val account: AccountRepository,
) {
    suspend fun register() {
        val token = source.token() ?: return
        val outcome = account.registerPushToken(token)
        if (outcome is Outcome.Failure) Logger.withTag("Push").w { "The push token was not registered: ${outcome.error}" }
    }
}
