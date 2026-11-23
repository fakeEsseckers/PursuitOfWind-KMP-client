package io.github.esseckers.pursuitofwind.core.domain.mapper

import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Today and the next two days: the days the app decides on. */
internal const val DAYS_DECIDED = 3

/**
 * The engine's decisions for the days the app decides on. Today shows them and the background check announces their
 * windows, so both go through here and never disagree about which days count.
 */
internal object DayDecisionsMapper {
    /**
     * One decision per day from the rider's today in [riderZone] on, at most [days] of them: a spot behind the rider's
     * time zone can still be on the rider's yesterday, and that day is left out.
     */
    fun decide(
        forecasts: List<SpotForecast>,
        rider: RiderProfile,
        now: Instant,
        riderZone: TimeZone,
        days: Int = DAYS_DECIDED,
    ): List<DayDecision> {
        val today = now.toLocalDateTime(riderZone).date
        return DecisionEngine.decideDays(forecasts, rider, now).filter { it.date >= today }.take(days)
    }
}
