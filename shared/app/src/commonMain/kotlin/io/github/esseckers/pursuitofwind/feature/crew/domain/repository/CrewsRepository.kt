package io.github.esseckers.pursuitofwind.feature.crew.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.feature.crew.domain.entity.CrewDetailsEntity
import io.github.esseckers.pursuitofwind.feature.crew.domain.mapper.CrewChoiceMapper
import kotlinx.coroutines.flow.Flow

/**
 * The rider's crews, their members and rides, and what the rider does in them. The cache is the source of truth;
 * calls return their error, or null when they worked, and never throw, apart from cancellation.
 */
internal interface CrewsRepository {
    /** The crews the rider belongs to, oldest first, as cached. */
    val crews: Flow<Cached<List<Crew>>>

    /** Members, rides and the rides' spots of [crewId], as cached; emits again whenever any of them changes. */
    fun details(crewId: String): Flow<CrewDetailsEntity>

    /** Fetches the list of crews, then members and rides of the crew shown for [chosen] (`CrewChoiceMapper.shownCrew`). */
    suspend fun refreshAll(chosen: String?): AppError?

    /** Brings members and rides of [crewId] up to date, and the names of spots its rides go to. */
    suspend fun refresh(crewId: String): AppError?

    suspend fun create(name: String): Outcome<Crew>

    /** Joins the crew with [inviteCode]; case and surrounding spaces do not matter. */
    suspend fun join(inviteCode: String): Outcome<Crew>

    /** Shows the seat as taken at once; a seat someone else took first fails with `Conflict(SEATS_FULL)`. */
    suspend fun takeSeat(rideId: String): AppError?

    suspend fun leaveRide(rideId: String): AppError?

    /** Driver only. */
    suspend fun cancelRide(rideId: String): AppError?
}
