package io.github.esseckers.pursuitofwind.core.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** How old cached data may get, the same on every screen. */
internal object Freshness {
    /** Coming back to a screen refreshes it when its last refresh is older than this. */
    val REFRESH_AFTER: Duration = 30.minutes

    /** A forecast older than this gets the "getting old" notice; the server updates forecasts every hour. */
    val STALE_AFTER: Duration = 3.hours
}
