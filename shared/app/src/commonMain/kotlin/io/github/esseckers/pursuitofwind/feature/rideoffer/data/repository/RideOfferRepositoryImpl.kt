package io.github.esseckers.pursuitofwind.feature.rideoffer.data.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.repository.RideOfferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [RideOfferRepository] over the shared crew, spot and ride repositories. */
internal class RideOfferRepositoryImpl(
    crewRepository: CrewRepository,
    private val spots: SpotRepository,
    private val rides: RideRepository,
) : RideOfferRepository {
    override val crews: Flow<Cached<List<Crew>>> = crewRepository.crews

    // A GO window is at a followed spot, and followed spots are always in the cached catalog.
    override fun spot(spotId: SpotId): Flow<Spot?> =
        spots.catalog.map { cached -> cached.value.firstOrNull { it.id == spotId } }.distinctUntilChanged()

    override suspend fun offer(
        crewId: String,
        plan: RidePlan,
    ): AppError? = rides.offer(crewId, plan).errorOrNull()
}
