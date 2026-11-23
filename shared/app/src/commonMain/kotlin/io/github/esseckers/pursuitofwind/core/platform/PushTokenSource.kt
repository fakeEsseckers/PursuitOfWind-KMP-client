package io.github.esseckers.pursuitofwind.core.platform

/**
 * The device's token for pushes from the server: FCM's on Android, APNs' on iOS. Each platform binds its own in its
 * Koin platform module. The client phase has no push service, so every platform binds [NoPushToken]; the server phase
 * replaces that binding and nothing else (ADR 0011, ADR 0012).
 */
internal interface PushTokenSource {
    /** The current token, or null while the device has none. */
    suspend fun token(): String?
}

/** No push service yet: nothing to register. */
internal object NoPushToken : PushTokenSource {
    override suspend fun token(): String? = null
}
