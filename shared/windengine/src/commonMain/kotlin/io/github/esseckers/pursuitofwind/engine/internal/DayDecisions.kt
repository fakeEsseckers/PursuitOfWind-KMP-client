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
    val tooFar = spot.isTooFarFor(profile)
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
    if (ranked.isNotEmpty()) return DayDecision.Go(date, ranked.first(), ranked.drop(1))
    val details = days.map { SpotNoGo(it.spot.id, if (it.tooFar) Reason.TOO_FAR else noGoReason(listOf(it))) }.sortedBy { it.spotId }
    val primaryReason = if (reachable.isEmpty()) Reason.TOO_FAR else noGoReason(reachable)
    return DayDecision.NoGo(date, primaryReason, details, nextGoDay = null)
}

/**
 * WINDOW_TOO_SHORT if any hour was GO (its window did not qualify); OUTSIDE_RIDING_HOURS if an hour would have been GO
 * but for the riding hours. Otherwise the most frequent reason across the daylight hours within the riding hours, or
 * across all daylight hours when none lies within them; DARK when there is no daylight hour.
 */
private fun noGoReason(days: List<SpotDay>): Reason {
    val verdicts = days.flatMap { it.verdicts }
    val daylight = verdicts.filterNot { Reason.DARK in it.reasons }
    val riding = daylight.filterNot { Reason.OUTSIDE_RIDING_HOURS in it.reasons }
    return when {
        verdicts.any { it.verdict == Verdict.GO } -> Reason.WINDOW_TOO_SHORT
        verdicts.any { it.reasons == listOf(Reason.OUTSIDE_RIDING_HOURS) } -> Reason.OUTSIDE_RIDING_HOURS
        else -> mostFrequentReason(riding.ifEmpty { daylight }) ?: Reason.DARK
    }
}

/**
 * The reason that most of [verdicts] share, counting every reason of an hour but OUTSIDE_RIDING_HOURS, the more severe
 * one on a tie.
 */
private fun mostFrequentReason(verdicts: List<HourVerdict>): Reason? =
    verdicts
        .flatMap { it.reasons - Reason.OUTSIDE_RIDING_HOURS }
        .groupingBy { it }
        .eachCount()
        .entries
        .minWithOrNull(compareByDescending<Map.Entry<Reason, Int>> { it.value }.thenBy { it.key })
        ?.key

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
