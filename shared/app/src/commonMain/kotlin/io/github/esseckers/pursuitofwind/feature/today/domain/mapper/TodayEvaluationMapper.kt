package io.github.esseckers.pursuitofwind.feature.today.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.DayEvaluationEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.SpotForecastEntity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Today and the next two days. */
internal const val DAYS_SHOWN = 3

/** The engine's answer for Today over the favorite spots' cached forecasts. */
internal object TodayEvaluationMapper {
    /**
     * The days from the rider's today in [riderZone] on, at most [days] of them: a spot behind the rider's time zone can
     * still be on the rider's yesterday, and that day is left out.
     */
    fun evaluate(
        profile: Profile,
        spots: List<SpotForecastEntity>,
        now: Instant,
        riderZone: TimeZone,
        days: Int = DAYS_SHOWN,
    ): List<DayEvaluationEntity> {
        val configs = spots.associate { it.spot.spot.id to it.spot.toSpotConfig() }
        val hours = spots.associate { it.spot.spot.id to it.hours }
        val today = now.toLocalDateTime(riderZone).date
        return DecisionEngine
            .decideDays(configs.map { (id, config) -> SpotForecast(config, hours.getValue(id)) }, profile.rider, now)
            .filter { it.date >= today }
            .take(days)
            .map { decision ->
                when (decision) {
                    is DayDecision.Go -> {
                        val wind =
                            ridingWind(
                                decision.window,
                                configs.getValue(decision.window.spotId),
                                hours.getValue(decision.window.spotId),
                                profile,
                            )
                        DayEvaluationEntity(decision, wind.minOrNull(), wind.maxOrNull())
                    }

                    is DayDecision.NoGo -> {
                        DayEvaluationEntity(decision)
                    }
                }
            }
    }

    /** The consensus mean wind of each hour the rider can actually ride in [window]: from the arrival to its end. */
    private fun ridingWind(
        window: SessionWindow,
        config: SpotConfig,
        hours: List<ForecastHour>,
        profile: Profile,
    ) = hours
        .filter { it.start < window.end && it.start + 1.hours > window.usableStart }
        .map { DecisionEngine.evaluateHour(it, profile.rider, config).wind.mean }
}
