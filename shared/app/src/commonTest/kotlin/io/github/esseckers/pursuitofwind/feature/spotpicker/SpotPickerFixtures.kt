package io.github.esseckers.pursuitofwind.feature.spotpicker

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.model.SpotPickerSnapshot
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerInputs
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerPreviewData
import kotlin.time.Duration
import kotlin.time.Instant

val CATALOG: List<Spot> =
    with(SpotPickerPreviewData) { listOf(dune, lakeWeiss, nordstrand, saltLagoon) }

val FETCHED_AT: Instant = Instant.parse("2026-10-24T06:00:00Z")

/** The catalog, with [mySpots] followed. */
internal fun snapshot(vararg mySpots: MySpot): SpotPickerSnapshot = SpotPickerSnapshot(Cached(CATALOG, FETCHED_AT), mySpots.toList())

fun following(
    spot: Spot,
    drive: Duration,
    favorite: Boolean = true,
): MySpot = MySpot(spot, drive, favorite)

internal fun inputs(vararg mySpots: MySpot): SpotPickerInputs = SpotPickerInputs(snapshot = snapshot(*mySpots))
