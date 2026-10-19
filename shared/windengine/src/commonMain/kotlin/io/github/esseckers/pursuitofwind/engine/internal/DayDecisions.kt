package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import io.github.esseckers.pursuitofwind.engine.model.SpotNoGo
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private const val MAX_ALTERNATIVES = 2

/** One spot on one local date. */
private class SpotDay(
    val spot: SpotConfig,
    val verdicts: List<HourVerdict>,
    val windows: List<SessionWindow>,
    val tooFar: Boolean,
)

/**
 * One decision per local date from today on, in date order. Each spot's hours are split into days in the spot's own
 * time zone, so windows never cross local midnight; a date covers the spots that have forecast hours on it.
 * Dates come from reachable spots only: a far spot with a longer forecast must not turn the extra days into TOO_FAR.
 */
internal fun dayDecisions(
    forecasts: List<SpotForecast>,
    profile: RiderProfile,
    now: Instant,
): List<DayDecision> {
    require(forecasts.map { it.spot.id }.toSet().size == forecasts.size) { "Each spot may appear only once" }
    val drives = forecasts.associate { it.spot.id to it.spot.drive }
    val ranking = windowRanking { drives.getValue(it) }
    val allDays = forecasts.flatMap { spotDays(it, profile, now) }
    val reachableDates = allDays.filterNot { it.second.tooFar }.map { it.first }.toSet()
    val dates = reachableDates.ifEmpty { allDays.map { it.first }.toSet() }
    val daysByDate = allDays.filter { it.first in dates }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
    val decisions = daysByDate.keys.sorted().map { date -> decideDay(date, daysByDate.getValue(date), ranking) }
    return withNextGoDays(decisions)
}

private fun spotDays(
    forecast: SpotForecast,
    profile: RiderProfile,
    now: Instant,
): List<Pair<LocalDate, SpotDay>> {
    val spot = forecast.spot
    val today = now.localDate(spot.timeZone)
    val tooFar = spot.drive > profile.maxDrive
    return groupByLocalDate(forecast.hours.sortedBy { it.start }, spot.timeZone) { it.start }
        .filterKeys { it >= today }
        .map { (date, hours) ->
            val verdicts = if (tooFar) emptyList() else hours.map { hourVerdict(it, profile, spot) }
            date to SpotDay(spot, verdicts, windowsAt(spot, verdicts, profile, now), tooFar)
        }
}

private fun decideDay(
    date: LocalDate,
    days: List<SpotDay>,
    ranking: Comparator<SessionWindow>,
): DayDecision {
    val reachable = days.filterNot { it.tooFar }
    val ranked = reachable.flatMap { it.windows }.filter { it.qualifies }.sortedWith(ranking)
    if (ranked.isNotEmpty()) return DayDecision.Go(date, ranked.first(), ranked.drop(1).take(MAX_ALTERNATIVES))
    val details = days.map { SpotNoGo(it.spot.id, if (it.tooFar) Reason.TOO_FAR else noGoReason(listOf(it))) }.sortedBy { it.spotId }
    val primaryReason = if (reachable.isEmpty()) Reason.TOO_FAR else noGoReason(reachable)
    return DayDecision.NoGo(date, primaryReason, details, nextGoDay = null)
}

/**
 * WINDOW_TOO_SHORT if any hour was GO (its window did not qualify). Otherwise the most frequent reason across
 * daylight hours, the more severe one on a tie; DARK when there is no daylight hour at all.
 */
private fun noGoReason(days: List<SpotDay>): Reason {
    val verdicts = days.flatMap { it.verdicts }
    if (verdicts.any { it.verdict == Verdict.GO }) return Reason.WINDOW_TOO_SHORT
    val counts =
        verdicts
            .filterNot { Reason.DARK in it.reasons }
            .flatMap { it.reasons }
            .groupingBy { it }
            .eachCount()
    return counts.entries.minWithOrNull(compareByDescending<Map.Entry<Reason, Int>> { it.value }.thenBy { it.key })?.key
        ?: Reason.DARK
}

private fun withNextGoDays(decisions: List<DayDecision>): List<DayDecision> {
    var nextGo: LocalDate? = null
    return decisions
        .reversed()
        .map { decision ->
            when (decision) {
                is DayDecision.Go -> decision.also { nextGo = it.date }
                is DayDecision.NoGo -> decision.copy(nextGoDay = nextGo)
            }
        }.reversed()
}
