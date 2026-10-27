package io.github.esseckers.pursuitofwind.feature.spots.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.domain.entity.SpotSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * The spots the rider follows, and changing them. The cache is the source of truth; calls return their error and never
 * throw, apart from cancellation.
 *
 * Writes (a save, a removal, a restore) run one at a time in the order they were asked for, and each finishes even when
 * its caller is cancelled, so a change the rider made is not lost when they leave the tab.
 */
internal interface SpotsRepository {
    /** The followed spots, by name, as cached; emits again whenever they change. */
    val spots: Flow<Cached<List<MySpot>>>

    /** Fetches the followed spots from the server. */
    suspend fun refresh(): AppError?

    /**
     * Saves [wanted] for [spotId] until the server has its latest value. [wanted] is asked again after each request, so
     * changes made meanwhile go out together in one more request; null stops the save (the spot is being removed).
     * Gives the followed spots as cached once saved, null if the cache cannot be read.
     */
    suspend fun save(
        spotId: SpotId,
        wanted: () -> SpotSettingsEntity?,
    ): Outcome<Cached<List<MySpot>>?>

    /** Stops following [spotId]. Gives the followed spots as cached afterwards, null if the cache cannot be read. */
    suspend fun remove(spotId: SpotId): Outcome<Cached<List<MySpot>>?>

    /**
     * Follows [spotId] again with [settings], unless it was followed again meanwhile (from the spot picker, say): what
     * the rider chose there wins over the old values.
     */
    suspend fun restore(
        spotId: SpotId,
        settings: SpotSettingsEntity,
    ): AppError?
}
