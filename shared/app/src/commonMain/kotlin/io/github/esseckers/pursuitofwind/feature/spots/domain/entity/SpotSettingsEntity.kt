package io.github.esseckers.pursuitofwind.feature.spots.domain.entity

import kotlin.time.Duration

/** How the rider wants to follow a spot: the [drive] to it and whether it is a [favorite]. */
internal data class SpotSettingsEntity(
    val drive: Duration,
    val favorite: Boolean,
)
