package io.github.esseckers.pursuitofwind.data.fake

/** How the in-app fake server's "network" behaves. Switchable at runtime from the demo panel. */
public enum class NetworkMode {
    /** Responses come back immediately. */
    NORMAL,

    /** Every response takes two seconds. */
    SLOW,

    /** About 30% of requests fail with `503 SERVICE_UNAVAILABLE`, in the same seeded order on every run. */
    FLAKY,

    /** Every request fails with an `IOException`, as without a connection. */
    OFFLINE,
}
