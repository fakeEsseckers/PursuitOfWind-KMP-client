package io.github.esseckers.pursuitofwind.feature.today.data.repository

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.outcomeOf
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.CrewRidesEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.RefreshResultEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.mapper.RefreshErrorMapper
import io.github.esseckers.pursuitofwind.feature.today.domain.repository.TodayRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [TodayRepository] over the app's shared repositories: their cached flows combined into one snapshot, and a refresh
 * that calls each of them. The shared repositories stay the single source of truth; this class only combines them.
 */
internal class TodayRepositoryImpl(
    private val account: AccountRepository,
    private val profiles: ProfileRepository,
    private val spots: SpotRepository,
    private val forecasts: ForecastRepository,
    private val crews: CrewRepository,
    private val rides: RideRepository,
) : TodayRepository {
    override val snapshot: Flow<TodaySnapshotEntity> =
        combine(
            profiles.profile,
            spots.mySpots,
            favoriteForecasts(),
            crewRides(),
            account.userId,
        ) { profile, spots, forecasts, rides, user ->
            TodaySnapshotEntity(profile, spots, forecasts, CrewRidesEntity(rides.rides, rides.hasCrew, user))
        }

    private val log = Logger.withTag("Today")

    /**
     * Fetches the profile, the followed spots, the favorites' forecasts and the crews' rides. Never throws, apart from
     * cancellation: a failure to read the cache is reported as its [AppError], such as [AppError.Storage].
     */
    override suspend fun refresh(): RefreshResultEntity =
        when (val refreshed = outcomeOf { refreshAll() }) {
            is Outcome.Success -> {
                refreshed.value
            }

            is Outcome.Failure -> {
                log.e { "Refreshing Today failed: ${refreshed.error}" }
                RefreshResultEntity(forecastError = refreshed.error)
            }
        }

    private suspend fun refreshAll(): RefreshResultEntity =
        coroutineScope {
            val profile = async { profiles.refresh() }
            val crew = async { refreshCrews() }
            val followed = spots.refreshMySpots()
            val favorites =
                spots.mySpots
                    .first()
                    .value
                    .filter { it.favorite }
            val forecastResults = favorites.map { async { forecasts.refresh(it.spot.id) } }.awaitAll()
            val forecastErrors = (listOf(profile.await(), followed) + forecastResults).mapNotNull { it.errorOrNull() }
            RefreshResultEntity(
                forecastError = RefreshErrorMapper.mainError(forecastErrors),
                crewError = RefreshErrorMapper.mainError(crew.await().mapNotNull { it.errorOrNull() }),
            )
        }

    /** The crew list, then each crew's feed, which brings its rides up to date. */
    private suspend fun refreshCrews(): List<Outcome<Unit>> {
        val list = crews.refreshCrews()
        return if (list is Outcome.Failure) {
            listOf(list)
        } else {
            crews.crews
                .first()
                .value
                .map { crews.sync(it.id) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun favoriteForecasts(): Flow<Map<SpotId, Cached<List<ForecastHour>>>> =
        spots.mySpots
            .map { cached -> cached.value.filter { it.favorite }.map { it.spot.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> forecasts.forecast(id).map { id to it } }) { pairs -> pairs.toMap() }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun crewRides(): Flow<RidesOfCrews> =
        crews.crews
            .map { cached -> cached.value.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(RidesOfCrews(emptyList(), hasCrew = false))
                } else {
                    combine(
                        ids.map { id ->
                            rides.rides(id).map { it.value }
                        },
                    ) { lists -> RidesOfCrews(lists.flatMap { it }, hasCrew = true) }
                }
            }

    private data class RidesOfCrews(
        val rides: List<Ride>,
        val hasCrew: Boolean,
    )
}
