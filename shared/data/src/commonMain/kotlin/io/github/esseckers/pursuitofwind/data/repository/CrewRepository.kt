package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.data.internal.local.CrewEntity
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.cached
import io.github.esseckers.pursuitofwind.data.internal.local.markUpdated
import io.github.esseckers.pursuitofwind.data.internal.mapping.toCrew
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import io.github.esseckers.pursuitofwind.data.internal.mapping.toMember
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.internal.newIdempotencyKey
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewFeedSync
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.CrewMember
import io.github.esseckers.pursuitofwind.data.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** The rider's crews and their members. Rides are in [RideRepository]; [sync] keeps both current. */
public class CrewRepository internal constructor(
    private val api: PursuitOfWindApi,
    private val database: PursuitOfWindDatabase,
    private val feed: CrewFeedSync,
    private val clock: Clock,
) {
    private val crewDao = database.crews()
    private val sync = database.syncState()

    /**
     * One change to the list of crews at a time, request and cache write together. [refreshCrews] replaces the list
     * with the server's, so a refresh answered before a new crew was created or joined but written after it would drop
     * that crew from the cache.
     */
    private val crewListChanges = Mutex()

    public val crews: Flow<Cached<List<Crew>>> = sync.cached(SyncKeys.CREWS, crewDao.crews().map { list -> list.map { it.toCrew() } })

    public fun members(crewId: String): Flow<Cached<List<CrewMember>>> =
        sync.cached(SyncKeys.members(crewId), crewDao.members(crewId).map { list -> list.map { it.toMember() } })

    public suspend fun refreshCrews(): Outcome<Unit> =
        outcome {
            crewListChanges.withLock {
                val crews = api.myCrews().items
                database.withWriteTransaction {
                    crewDao.deleteCrews()
                    crewDao.upsertCrews(crews.map { it.toEntity() })
                    sync.markUpdated(SyncKeys.CREWS, clock.now())
                }
            }
        }

    public suspend fun refreshMembers(crewId: String): Outcome<Unit> = outcome { feed.refreshMembers(crewId) }

    public suspend fun create(name: String): Outcome<Crew> =
        outcome { crewListChanges.withLock { save(api.createCrew(name, newIdempotencyKey()).toEntity()) } }

    /** Joins with an invite code (case and surrounding spaces do not matter). Joining twice is fine. */
    public suspend fun join(inviteCode: String): Outcome<Crew> =
        outcome { crewListChanges.withLock { save(api.joinCrew(inviteCode, newIdempotencyKey()).toEntity()) } }

    /**
     * Brings members and rides of [crewId] up to date from the crew feed: cheap when little changed, a full reload
     * the first time or after the server reset the feed.
     */
    public suspend fun sync(crewId: String): Outcome<Unit> = outcome { feed.sync(crewId) }

    private suspend fun save(crew: CrewEntity): Crew {
        crewDao.upsertCrews(listOf(crew))
        return crew.toCrew()
    }
}
