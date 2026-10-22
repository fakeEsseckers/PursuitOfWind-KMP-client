package io.github.esseckers.pursuitofwind.data.model

import kotlin.time.Instant

/**
 * A value from the local cache and when it was last fetched from the server; [updatedAt] is null when it never was.
 * Screens use it for "updated 2 h ago" and to decide whether to show stale data while refreshing.
 */
public data class Cached<out T>(
    val value: T,
    val updatedAt: Instant?,
)
