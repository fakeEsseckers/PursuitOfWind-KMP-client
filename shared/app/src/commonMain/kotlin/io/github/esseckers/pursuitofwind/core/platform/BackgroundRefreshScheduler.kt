package io.github.esseckers.pursuitofwind.core.platform

/**
 * Has the system run the check for new session windows while the app is closed, about once an hour and only as often
 * as the system allows. Each platform binds its own in its Koin platform module.
 */
internal interface BackgroundRefreshScheduler {
    /**
     * Whether this device runs the check at all. Where it does not, the app does not offer to tell the rider about new
     * windows, because it could not keep the promise.
     */
    val available: Boolean

    /** Starts the periodic check; one that is scheduled already keeps its schedule. */
    fun schedule()

    /** Stops the periodic check. */
    fun cancel()
}
