package io.github.esseckers.pursuitofwind.feature.spotpicker.data.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotChangeEntity
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotPickerSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.repository.SpotPickerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** [SpotPickerRepository] over the shared [SpotRepository], which stays the single source of truth. */
internal class SpotPickerRepositoryImpl(
    private val spots: SpotRepository,
) : SpotPickerRepository {
    override val snapshot: Flow<SpotPickerSnapshotEntity> =
        combine(spots.catalog, spots.mySpots) { catalog, mine -> SpotPickerSnapshotEntity(catalog, mine.value) }

    override suspend fun refresh(): AppError? =
        coroutineScope {
            val catalog = async { spots.refreshCatalog() }
            val mine = spots.refreshMySpots()
            catalog.await().errorOrNull() ?: mine.errorOrNull()
        }

    override suspend fun save(changes: List<SpotChangeEntity>): AppError? {
        for (change in changes) {
            spots.follow(change.spotId, change.drive, change.favorite).errorOrNull()?.let { return it }
        }
        return null
    }
}
