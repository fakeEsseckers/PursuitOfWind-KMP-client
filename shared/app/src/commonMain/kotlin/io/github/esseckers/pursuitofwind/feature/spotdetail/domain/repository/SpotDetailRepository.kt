package io.github.esseckers.pursuitofwind.feature.spotdetail.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.model.SpotDetailSnapshot
import kotlinx.coroutines.flow.Flow

/** What the spot detail screen needs from the data layer. */
internal interface SpotDetailRepository {
    /** The cached data of [spotId]; emits again whenever any part of it changes. */
    fun snapshot(spotId: SpotId): Flow<SpotDetailSnapshot>

    /** Fetches the forecast of [spotId] into the cache. Null when it worked; never throws, apart from cancellation. */
    suspend fun refresh(spotId: SpotId): AppError?
}
