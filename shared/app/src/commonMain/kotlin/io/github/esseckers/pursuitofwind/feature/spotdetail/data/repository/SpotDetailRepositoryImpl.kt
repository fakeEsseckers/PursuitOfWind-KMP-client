package io.github.esseckers.pursuitofwind.feature.spotdetail.data.repository

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.model.SpotDetailSnapshot
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.repository.SpotDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** [SpotDetailRepository] over the shared repositories, which stay the single source of truth. */
internal class SpotDetailRepositoryImpl(
    private val profiles: ProfileRepository,
    private val spots: SpotRepository,
    private val forecasts: ForecastRepository,
) : SpotDetailRepository {
    override fun snapshot(spotId: SpotId): Flow<SpotDetailSnapshot> =
        combine(
            profiles.profile,
            spots.mySpots.map { cached -> Cached(cached.value.firstOrNull { it.spot.id == spotId }, cached.updatedAt) },
            forecasts.forecast(spotId),
        ) { profile, spot, forecast -> SpotDetailSnapshot(profile, spot, forecast) }

    override suspend fun refresh(spotId: SpotId): AppError? =
        try {
            forecasts.refresh(spotId).errorOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Any storage failure: the screen reports it instead of crashing.
            @Suppress("TooGenericExceptionCaught")
            failure: Exception,
        ) {
            log.e(failure) { "Refreshing the forecast of $spotId failed" }
            AppError.Unknown(failure.toString())
        }

    private val log = Logger.withTag("SpotDetail")
}
