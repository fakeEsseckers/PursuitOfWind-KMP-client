package io.github.esseckers.pursuitofwind.feature.crew.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.Crew

/** Which of the rider's crews the Crew tab shows. */
internal object CrewChoiceMapper {
    /** The one the rider [chosen], or the first of [crews] when none is chosen or it is gone; null without crews. */
    fun shownCrew(
        crews: List<Crew>,
        chosen: String?,
    ): Crew? = crews.firstOrNull { it.id == chosen } ?: crews.firstOrNull()
}
