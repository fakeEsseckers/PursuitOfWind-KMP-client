package io.github.esseckers.pursuitofwind.core.platform

/**
 * Whether the system lets the app show notifications, and the ways to change that. Each platform binds its own in its
 * Koin platform module.
 */
internal interface NotificationPermission {
    /** True when a notification from the app would show right now. */
    suspend fun granted(): Boolean

    /**
     * Asks the rider with the system's dialog, where the system still shows one, and says whether notifications show
     * afterwards. Once the rider has refused, the system stops asking: this is then false at once, and [openSettings]
     * is the way to turn them on.
     */
    suspend fun request(): Boolean

    /** Opens the system's settings of this app's notifications. */
    fun openSettings()
}
