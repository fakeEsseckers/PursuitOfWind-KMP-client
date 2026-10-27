package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.CreateSpotRequestDto
import io.github.esseckers.pursuitofwind.contract.GeoPointDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.contract.SpotUpdateDto
import io.github.esseckers.pursuitofwind.data.internal.local.MySpotEntity
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.cacheAnswer
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
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The spot catalog, which riders fill by adding spots, and the spots the rider follows. Reads come from the cache;
 * `refresh…` updates it.
 */
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

    /**
     * A change to a spot and a catalog refresh one at a time, request and cache write together, so a refresh answered
     * before the change and written after it cannot bring the old spot back. A change takes this lock before
     * [mySpotsChanges], which a refresh of the followed spots holds while it writes them. Adding a spot needs neither:
     * a refresh only adds and updates spots, so it cannot take a new one away.
     */
    private val catalogChanges = Mutex()

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
            catalogChanges.withLock {
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
        }

    /**
     * Adds a spot at [location] whose open water lies towards [water] to the catalog, for every rider, and caches it.
     * The server works out its sectors from [water]; [timeZone] is a first guess it may correct. The rider does not
     * follow the spot yet: [follow] does that.
     */
    public suspend fun addSpot(
        name: String,
        location: GeoPoint,
        water: Bearing,
        timeZone: TimeZone,
    ): Outcome<Spot> =
        outcome {
            val request = CreateSpotRequestDto(name, location.toDto(), water.degrees, timeZone.id)
            cacheSpot(api.createSpot(request, newIdempotencyKey()))
        }

    /**
     * Renames or moves [spotId], or turns its [water], which only the rider who added it can: anyone else gets
     * [io.github.esseckers.pursuitofwind.data.model.AppError.Rejected] with `403`. Riders who follow it see the change
     * with their next list of spots.
     */
    public suspend fun changeSpot(
        spotId: SpotId,
        name: String,
        location: GeoPoint,
        water: Bearing,
    ): Outcome<Spot> =
        outcome {
            catalogChanges.withLock {
                mySpotsChanges.withLock {
                    cacheSpot(api.updateSpot(spotId.value, SpotUpdateDto(name, location.toDto(), water.degrees), newIdempotencyKey()))
                }
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
                cacheAnswer {
                    database.withWriteTransaction {
                        spots.upsertSpots(listOf(spot))
                        spots.upsertMySpots(listOf(MySpotEntity(spot.id, saved.driveMinutes, saved.favorite)))
                    }
                }
                MySpot(spot.toSpot(), saved.driveMinutes.minutes, saved.favorite)
            }
        }

    public suspend fun unfollow(spotId: SpotId): Outcome<Unit> =
        outcome {
            mySpotsChanges.withLock {
                api.deleteMySpot(spotId.value, newIdempotencyKey())
                cacheAnswer { spots.deleteMySpot(spotId.value) }
            }
        }

    /**
     * Caches [saved] as the server answered; the answer stands even when the cache cannot be written. A spot this device
     * cannot read (a time zone its database does not know) fails as [io.github.esseckers.pursuitofwind.data.model.AppError.Unknown],
     * as it would in any list of spots, though the server keeps it.
     */
    private suspend fun cacheSpot(saved: SpotDto): Spot {
        val entity = saved.toEntity()
        cacheAnswer { spots.upsertSpots(listOf(entity)) }
        return entity.toSpot()
    }

    private fun GeoPoint.toDto(): GeoPointDto = GeoPointDto(latitude, longitude)

    private companion object {
        /** The longest page the API gives. */
        const val CATALOG_PAGE_SIZE = ApiLimits.Paging.LIMIT_MAX
    }
}
