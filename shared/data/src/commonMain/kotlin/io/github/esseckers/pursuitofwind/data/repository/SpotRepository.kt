package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.data.internal.local.MySpotEntity
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.cached
import io.github.esseckers.pursuitofwind.data.internal.local.markUpdated
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import io.github.esseckers.pursuitofwind.data.internal.mapping.toMySpot
import io.github.esseckers.pursuitofwind.data.internal.mapping.toSpot
import io.github.esseckers.pursuitofwind.data.internal.mapping.validEntities
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.internal.newIdempotencyKey
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The spot catalog and the spots the rider follows. Reads come from the cache; `refresh…` updates it. */
public class SpotRepository internal constructor(
    private val api: PursuitOfWindApi,
    private val database: PursuitOfWindDatabase,
    private val clock: Clock,
) {
    private val spots = database.spots()
    private val sync = database.syncState()

    /**
     * One change to the followed spots at a time, request and cache write together. [refreshMySpots] replaces them all
     * with the server's list, so a refresh answered before a change but written after it would bring the old list back.
     */
    private val mySpotsChanges = Mutex()

    /** Every known spot, by name. Complete after [refreshCatalog]; before that, at least the followed spots. */
    public val catalog: Flow<Cached<List<Spot>>> = sync.cached(SyncKeys.CATALOG, spots.spots().map { list -> list.map { it.toSpot() } })

    /** Followed spots, by name. */
    public val mySpots: Flow<Cached<List<MySpot>>> =
        sync.cached(
            SyncKeys.MY_SPOTS,
            spots.mySpots().map { list ->
                list.map { it.toMySpot() }
            },
        )

    public suspend fun refreshCatalog(): Outcome<Unit> =
        outcome {
            val all = mutableListOf<SpotDto>()
            var cursor: String? = null
            do {
                val page = api.spotCatalogPage(cursor, CATALOG_PAGE_SIZE)
                all += page.items
                cursor = page.nextCursor
            } while (cursor != null)
            database.withWriteTransaction {
                spots.upsertSpots(validEntities(all))
                sync.markUpdated(SyncKeys.CATALOG, clock.now())
            }
        }

    public suspend fun refreshMySpots(): Outcome<Unit> =
        outcome {
            mySpotsChanges.withLock {
                val fetched = api.mySpots().items
                val valid = validEntities(fetched.map { it.spot })
                val ids = valid.map { it.id }.toSet()
                val items = fetched.filter { it.spot.id in ids }
                database.withWriteTransaction {
                    spots.upsertSpots(valid)
                    spots.deleteMySpots()
                    spots.upsertMySpots(items.map { MySpotEntity(it.spot.id, it.driveMinutes, it.favorite) })
                    sync.markUpdated(SyncKeys.MY_SPOTS, clock.now())
                }
            }
        }

    /** Follows [spotId], or changes the drive time and favorite flag of a followed spot. */
    public suspend fun follow(
        spotId: SpotId,
        drive: Duration,
        favorite: Boolean,
    ): Outcome<MySpot> =
        outcome {
            mySpotsChanges.withLock {
                val saved = api.putMySpot(spotId.value, MySpotUpdateDto(drive.inWholeMinutes.toInt(), favorite), newIdempotencyKey())
                val spot = saved.spot.toEntity()
                database.withWriteTransaction {
                    spots.upsertSpots(listOf(spot))
                    spots.upsertMySpots(listOf(MySpotEntity(spot.id, saved.driveMinutes, saved.favorite)))
                }
                MySpot(spot.toSpot(), saved.driveMinutes.minutes, saved.favorite)
            }
        }

    public suspend fun unfollow(spotId: SpotId): Outcome<Unit> =
        outcome {
            mySpotsChanges.withLock {
                api.deleteMySpot(spotId.value, newIdempotencyKey())
                spots.deleteMySpot(spotId.value)
            }
        }

    private companion object {
        /** The longest page the API gives. */
        const val CATALOG_PAGE_SIZE = ApiLimits.Paging.LIMIT_MAX
    }
}
