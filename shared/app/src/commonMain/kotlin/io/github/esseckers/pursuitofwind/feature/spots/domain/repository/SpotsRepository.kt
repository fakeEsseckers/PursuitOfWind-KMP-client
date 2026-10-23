package io.github.esseckers.pursuitofwind.feature.spots.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * The spots the rider follows, and changing them. The cache is the source of truth; each call returns its error, or
 * null when it worked, and never throws, apart from cancellation.
 */
internal interface SpotsRepository {
    /** The followed spots, by name, as cached; emits again whenever they change. */
    val spots: Flow<Cached<List<MySpot>>>

    /** The followed spots as cached right now, so read after a write they show it; null if the cache cannot be read. */
    suspend fun current(): Cached<List<MySpot>>?

    /** Fetches the followed spots from the server. */
    suspend fun refresh(): AppError?

    /** Follows [spotId] with [drive] and [favorite], or changes both for a followed spot. */
    suspend fun update(
        spotId: SpotId,
        drive: Duration,
        favorite: Boolean,
    ): AppError?

    suspend fun remove(spotId: SpotId): AppError?
}
