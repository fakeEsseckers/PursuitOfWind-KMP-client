package io.github.esseckers.pursuitofwind.feature.spotdetail.data.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotDetailSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.repository.SpotDetailRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** [SpotDetailRepository] over the shared repositories, which stay the single source of truth. */
internal class SpotDetailRepositoryImpl(
    private val profiles: ProfileRepository,
    private val spots: SpotRepository,
    private val forecasts: ForecastRepository,
) : SpotDetailRepository {
    override fun snapshot(spotId: SpotId): Flow<SpotDetailSnapshotEntity> =
        combine(
            profiles.profile,
            spots.mySpots.map { cached -> Cached(cached.value.firstOrNull { it.spot.id == spotId }, cached.updatedAt) },
            forecasts.forecast(spotId),
        ) { profile, spot, forecast -> SpotDetailSnapshotEntity(profile, spot, forecast) }

    override suspend fun refresh(spotId: SpotId): AppError? = forecasts.refresh(spotId).errorOrNull()
}
