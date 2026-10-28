package io.github.esseckers.pursuitofwind.feature.spots.domain.entity

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot

/**
 * What the Spots tab shows, as cached right now: the followed [spots], and the signed-in rider ([riderId], null before
 * the first sign-in), who may change the spots they added.
 */
internal data class SpotsSnapshotEntity(
    val spots: Cached<List<MySpot>>,
    val riderId: String?,
)
