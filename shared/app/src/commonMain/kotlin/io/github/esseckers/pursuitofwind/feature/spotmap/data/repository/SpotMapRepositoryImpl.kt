package io.github.esseckers.pursuitofwind.feature.spotmap.data.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.AddedSpotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.NewSpotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotEditEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.repository.SpotMapRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

/** [SpotMapRepository] over the shared [SpotRepository], which stays the single source of truth. */
internal class SpotMapRepositoryImpl(
    private val spots: SpotRepository,
    account: AccountRepository,
) : SpotMapRepository {
    override val snapshot: Flow<SpotMapSnapshotEntity> =
        combine(
            spots.catalog,
            spots.mySpots,
            account.userId,
        ) { catalog, mine, userId -> SpotMapSnapshotEntity(catalog, mine.value, userId) }

    override suspend fun refresh(): AppError? =
        coroutineScope {
            val catalog = async { spots.refreshCatalog() }
            val mine = spots.refreshMySpots()
            catalog.await().errorOrNull() ?: mine.errorOrNull()
        }

    override suspend fun add(
        spot: NewSpotEntity,
        deviceZone: TimeZone,
    ): AddedSpotEntity =
        withContext(NonCancellable) {
            when (val created = spots.addSpot(spot.name, spot.location, spot.water, zoneForServer(deviceZone))) {
                is Outcome.Success -> {
                    val spotId = created.value.id
                    AddedSpotEntity(spotId, spots.follow(spotId, spot.drive, favorite = true).errorOrNull())
                }

                is Outcome.Failure -> {
                    AddedSpotEntity(spotId = null, error = created.error)
                }
            }
        }

    override suspend fun follow(
        spotId: SpotId,
        drive: Duration,
    ): AppError? = withContext(NonCancellable) { spots.follow(spotId, drive, favorite = true).errorOrNull() }

    override suspend fun change(edit: SpotEditEntity): AppError? =
        withContext(NonCancellable) { spots.changeSpot(edit.spotId, edit.name, edit.location, edit.water).errorOrNull() }

    /** The API takes a region id (`Europe/Berlin`) or UTC; a device set to a fixed offset sends UTC, which the server corrects. */
    private fun zoneForServer(zone: TimeZone): TimeZone = if ('/' in zone.id || zone == TimeZone.UTC) zone else TimeZone.UTC
}
