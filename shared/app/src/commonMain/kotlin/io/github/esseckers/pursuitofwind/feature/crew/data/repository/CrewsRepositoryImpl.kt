package io.github.esseckers.pursuitofwind.feature.crew.data.repository

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.outcomeOf
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.feature.crew.domain.entity.CrewDetailsEntity
import io.github.esseckers.pursuitofwind.feature.crew.domain.repository.CrewsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** [CrewsRepository] over the shared crew, ride, spot and account repositories, which stay the source of truth. */
internal class CrewsRepositoryImpl(
    private val crewRepository: CrewRepository,
    private val rides: RideRepository,
    private val spots: SpotRepository,
    private val account: AccountRepository,
) : CrewsRepository {
    override val crews: Flow<Cached<List<Crew>>> = crewRepository.crews

    override fun details(crewId: String): Flow<CrewDetailsEntity> =
        combine(crewRepository.members(crewId), rides.rides(crewId), spots.catalog, account.userId) { members, rides, catalog, user ->
            CrewDetailsEntity(crewId, members, rides, catalog.value.associateBy { it.id }, user)
        }

    override suspend fun refreshAll(shown: (crews: List<Crew>) -> String?): AppError? =
        crewRepository.refreshCrews().errorOrNull() ?: readCache {
            shown(crewRepository.crews.first().value)?.let { refresh(it) }
        }

    /**
     * The crew feed brings members and rides up to date. Members of a crew offer rides to spots the rider may not
     * follow, so the catalog is fetched when a ride goes to a spot it lacks.
     */
    override suspend fun refresh(crewId: String): AppError? {
        val synced = crewRepository.sync(crewId).errorOrNull()
        return synced ?: catalogCovering(crewId)
    }

    override suspend fun create(name: String): Outcome<Crew> = crewRepository.create(name)

    override suspend fun join(inviteCode: String): Outcome<Crew> = crewRepository.join(inviteCode)

    override suspend fun takeSeat(rideId: String): AppError? = withContext(NonCancellable) { rides.takeSeat(rideId).errorOrNull() }

    override suspend fun leaveRide(rideId: String): AppError? = withContext(NonCancellable) { rides.releaseSeat(rideId).errorOrNull() }

    override suspend fun cancelRide(rideId: String): AppError? = withContext(NonCancellable) { rides.cancel(rideId).errorOrNull() }

    private suspend fun catalogCovering(crewId: String): AppError? =
        readCache {
            val known =
                spots.catalog
                    .first()
                    .value
                    .map { it.id }
                    .toSet()
            val missing =
                rides
                    .rides(crewId)
                    .first()
                    .value
                    .any { it.spotId !in known }
            if (missing) spots.refreshCatalog().errorOrNull() else null
        }

    /** Runs [block], which reads the cache; a failure to read it is reported ([AppError.Storage]) instead of thrown. */
    private suspend fun readCache(block: suspend () -> AppError?): AppError? =
        when (val read = outcomeOf(block)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> read.error.also { Logger.withTag("Crew").e { "Reading the cache failed: $it" } }
        }
}
