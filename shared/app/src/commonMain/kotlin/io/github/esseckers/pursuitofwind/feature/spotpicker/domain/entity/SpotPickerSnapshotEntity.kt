package io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Duration

/** What the picker shows, as cached right now: the spot catalog and the spots the rider follows. */
internal data class SpotPickerSnapshotEntity(
    val catalog: Cached<List<Spot>>,
    val mySpots: List<MySpot>,
)

/** One spot to follow, or to update: its [drive] from home and whether it is a [favorite]. */
internal data class SpotChangeEntity(
    val spotId: SpotId,
    val drive: Duration,
    val favorite: Boolean,
)
