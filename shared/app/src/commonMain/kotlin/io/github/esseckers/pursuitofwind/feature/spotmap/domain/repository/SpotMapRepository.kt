package io.github.esseckers.pursuitofwind.feature.spotmap.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.AddedSpotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.NewSpotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotEditEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

/**
 * The spots on the map, adding one, following one and changing one the rider added. Cached data is the source of
 * truth; calls return their error and never throw, apart from cancellation. Writes finish even when the screen that
 * asked for them goes away.
 */
internal interface SpotMapRepository {
    /** The cached snapshot; emits again whenever the catalog, the followed spots or the rider change. */
    val snapshot: Flow<SpotMapSnapshotEntity>

    /** Fetches the catalog and the followed spots; null when both worked. */
    suspend fun refresh(): AppError?

    /**
     * Adds [spot] to the catalog for every rider and follows it as a favorite. [deviceZone] is the server's first guess
     * at the spot's time zone.
     */
    suspend fun add(
        spot: NewSpotEntity,
        deviceZone: TimeZone,
    ): AddedSpotEntity

    /** Follows [spotId] as a favorite with [drive] from home. */
    suspend fun follow(
        spotId: SpotId,
        drive: Duration,
    ): AppError?

    /** Renames or moves a spot the rider added, or turns its water. */
    suspend fun change(edit: SpotEditEntity): AppError?
}
