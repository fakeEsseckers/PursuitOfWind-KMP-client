package io.github.esseckers.pursuitofwind.feature.spotpicker.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.model.SpotChange
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.model.SpotPickerSnapshot
import kotlinx.coroutines.flow.Flow

/** The catalog and the rider's spots, and saving the rider's choices. Cached data is the source of truth. */
internal interface SpotPickerRepository {
    /** The cached snapshot; emits again whenever the catalog or the followed spots change. */
    val snapshot: Flow<SpotPickerSnapshot>

    /** Fetches the catalog and the followed spots; null when both worked. Never throws, apart from cancellation. */
    suspend fun refresh(): AppError?

    /**
     * Saves [changes] one by one and stops at the first failure, which it returns; null when all were saved. Each saved
     * change is cached at once, so a retry only sends what is left.
     */
    suspend fun save(changes: List<SpotChange>): AppError?
}
