package io.github.esseckers.pursuitofwind.feature.rideoffer.domain.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.Flow

/** What offering a ride needs: the rider's crews, the spot, and the offer itself. Never throws, apart from cancellation. */
internal interface RideOfferRepository {
    /** The crews the rider belongs to, oldest first, as cached. */
    val crews: Flow<Cached<List<Crew>>>

    /** [spotId] as cached, or null when the cache does not know it. */
    fun spot(spotId: SpotId): Flow<Spot?>

    /** Offers [plan] to [crewId]; the crew sees it in its feed. */
    suspend fun offer(
        crewId: String,
        plan: RidePlan,
    ): AppError?
}
