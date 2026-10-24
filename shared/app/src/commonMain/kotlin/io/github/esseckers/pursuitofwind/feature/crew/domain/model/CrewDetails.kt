package io.github.esseckers.pursuitofwind.feature.crew.domain.model

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.CrewMember
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/**
 * What the Crew tab shows of crew [crewId], as cached: its [members] and [rides], the [spots] the rides go to (by id,
 * for their names and time zones) and the signed-in [userId], to tell the rider from the others.
 */
internal data class CrewDetails(
    val crewId: String,
    val members: Cached<List<CrewMember>>,
    val rides: Cached<List<Ride>>,
    val spots: Map<SpotId, Spot>,
    val userId: String?,
)

/** The crew the tab shows: the one the rider [chosen], or the first of [crews] when none is chosen or it is gone. */
internal fun shownCrew(
    crews: List<Crew>,
    chosen: String?,
): Crew? = crews.firstOrNull { it.id == chosen } ?: crews.firstOrNull()
