package io.github.esseckers.pursuitofwind.data.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * A session window the rider has been told about, kept so the same window is not announced twice. Every forecast is a
 * new model run and can move a window's start by an hour, so a window is known by its spot, its [date] (local to the
 * spot) and the time it shares with [start] to [end], not by its exact start (ADR 0011).
 */
public data class NotifiedWindow(
    val spotId: SpotId,
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
)
