package io.github.esseckers.pursuitofwind.core.refresh

/** Why what a screen shows may not be current; it shows above the content, which stays. */
internal enum class RefreshNotice {
    /** The last refresh failed because the device is offline. */
    OFFLINE,

    /** The last refresh failed for another reason. */
    REFRESH_FAILED,

    /** The refresh worked earlier, but what it fetched is old now. */
    STALE,
}
