package io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Instant

/** The ride the rider is about to offer: the window at [spotId] from [windowStart] to [windowEnd], leaving at [departAt]. */
internal data class RideDraft(
    val spotId: SpotId,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
)
