package io.github.esseckers.pursuitofwind.feature.rideoffer.domain.entity

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Instant

/**
 * The ride the rider is about to offer: the window at [spotId] from [windowStart] to [windowEnd], and [departAt], the time
 * to leave, which may have passed. The form decides the departure from it.
 */
internal data class RideDraftEntity(
    val spotId: SpotId,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
)
