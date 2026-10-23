package io.github.esseckers.pursuitofwind.feature.spots.data.repository

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.domain.repository.SpotsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

/** [SpotsRepository] over the shared [SpotRepository], which stays the single source of truth. */
internal class SpotsRepositoryImpl(
    private val shared: SpotRepository,
) : SpotsRepository {
    override val spots: Flow<Cached<List<MySpot>>> = shared.mySpots

    override suspend fun current(): Cached<List<MySpot>>? =
        try {
            shared.mySpots.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Any storage failure: the tab goes on with what the cache emits.
            @Suppress("TooGenericExceptionCaught")
            failure: Exception,
        ) {
            Logger.withTag("SpotsRepository").e(failure) { "Reading the followed spots failed" }
            null
        }

    override suspend fun refresh(): AppError? = shared.refreshMySpots().errorOrNull()

    override suspend fun update(
        spotId: SpotId,
        drive: Duration,
        favorite: Boolean,
    ): AppError? = shared.follow(spotId, drive, favorite).errorOrNull()

    override suspend fun remove(spotId: SpotId): AppError? = shared.unfollow(spotId).errorOrNull()
}
