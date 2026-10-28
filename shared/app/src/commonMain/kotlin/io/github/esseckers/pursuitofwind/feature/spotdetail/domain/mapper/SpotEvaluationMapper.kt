package io.github.esseckers.pursuitofwind.feature.spotdetail.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotEvaluationEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Today and the next two days at the spot, as on Today. */
internal const val CHART_DAYS = 3

/** The engine's answer for one spot, and the rules for which of its windows the rider is shown first. */
internal object SpotEvaluationMapper {
    /**
     * The hours of [hours] that have not ended by [now], over at most [CHART_DAYS] days at the spot, judged for the
     * rider: a last day the cache does not cover until midnight is left out, because its windows would end where the data
     * ends.
     */
    fun evaluate(
        profile: Profile,
        spot: MySpot,
        hours: List<ForecastHour>,
        now: Instant,
    ): SpotEvaluationEntity {
        val config = spot.toSpotConfig()
        val shown = shownHours(hours, now, config.timeZone)
        val verdicts = shown.map { DecisionEngine.evaluateHour(it, profile.rider, config) }
        return SpotEvaluationEntity(shown, verdicts, DecisionEngine.findWindows(verdicts, config, profile.rider, now), config.timeZone)
    }

    /** The windows the rider can still reach; one whose riding time has run out before the arrival is left out. */
    fun reachableWindows(evaluation: SpotEvaluationEntity): List<SessionWindow> = evaluation.windows.filter { it.isReachable }

    /** The first window worth riding, if any. */
    fun firstWorthRiding(evaluation: SpotEvaluationEntity): SessionWindow? = evaluation.windows.firstOrNull { it.qualifies }

    /** The best window worth riding that starts on [date] at the spot, if any: ranked as the day decision ranks them. */
    fun bestWindowOn(
        evaluation: SpotEvaluationEntity,
        date: LocalDate,
    ): SessionWindow? {
        val windows = evaluation.windows.filter { it.qualifies && it.start.toLocalDateTime(evaluation.zone).date == date }
        // One spot, one drive: the drive decides nothing here.
        return DecisionEngine.bestFirst(windows) { Duration.ZERO }.firstOrNull()
    }

    private fun shownHours(
        hours: List<ForecastHour>,
        now: Instant,
        zone: TimeZone,
    ): List<ForecastHour> {
        val upcoming = hours.sortedBy { it.start }.filter { it.start + 1.hours > now }
        val dates = upcoming.map { it.start.toLocalDateTime(zone).date }.distinct()
        val days = dates.take(CHART_DAYS)
        val coveredUntilMidnight =
            dates.size > CHART_DAYS || upcoming.lastOrNull()?.let { (it.start + 1.hours).toLocalDateTime(zone).date > days.last() } == true
        val shown = if (days.size > 1 && !coveredUntilMidnight) days.dropLast(1) else days
        return upcoming.filter { it.start.toLocalDateTime(zone).date in shown }
    }
}
