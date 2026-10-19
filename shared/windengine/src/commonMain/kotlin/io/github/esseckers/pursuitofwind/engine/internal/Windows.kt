package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Time to park, pump up and rig after the drive. */
internal val RIG_TIME: Duration = 30.minutes

private val FORECAST_STEP: Duration = 1.hours

/**
 * Windows in a gap-free sequence of hour verdicts, as index ranges.
 *
 * A window is a maximal run of GO hours in which isolated MARGINAL hours (GO on both sides) are bridged.
 * A MARGINAL hour at either edge, or two MARGINAL hours in a row, ends the run, so a window always starts and ends on GO.
 */
internal fun windowRuns(verdicts: List<Verdict>): List<IntRange> {
    fun bridged(index: Int) =
        index > 0 && index < verdicts.lastIndex && verdicts[index - 1] == Verdict.GO && verdicts[index + 1] == Verdict.GO

    val inWindow = verdicts.indices.map { verdicts[it] == Verdict.GO || (verdicts[it] == Verdict.MARGINAL && bridged(it)) }
    val runs = mutableListOf<IntRange>()
    var runStart: Int? = null
    for (index in verdicts.indices) {
        val start = runStart
        if (inWindow[index] && start == null) runStart = index
        if (!inWindow[index] && start != null) {
            runs += start until index
            runStart = null
        }
    }
    runStart?.let { runs += it..verdicts.lastIndex }
    return runs
}

/** Splits hours sorted by start into runs of back-to-back hours: a missing hour always ends a window. */
internal fun contiguousSegments(sortedHours: List<HourVerdict>): List<List<HourVerdict>> {
    val segments = mutableListOf<MutableList<HourVerdict>>()
    for (hour in sortedHours) {
        val current = segments.lastOrNull()
        if (current != null && current.last().start + FORECAST_STEP == hour.start) current += hour else segments += mutableListOf(hour)
    }
    return segments
}

/** Every window at [spot], qualifying or not. A spot beyond the rider's maximum drive has none. */
internal fun windowsAt(
    spot: SpotConfig,
    hours: List<HourVerdict>,
    profile: RiderProfile,
    now: Instant,
): List<SessionWindow> {
    require(hours.map { it.start }.toSet().size == hours.size) { "Hours at spot ${spot.id} must have distinct start times" }
    if (spot.drive > profile.maxDrive) return emptyList()
    return contiguousSegments(hours.sortedBy { it.start }).flatMap { segment ->
        windowRuns(segment.map { it.verdict }).map { run -> sessionWindow(spot, segment.slice(run), profile, now) }
    }
}

private fun sessionWindow(
    spot: SpotConfig,
    hours: List<HourVerdict>,
    profile: RiderProfile,
    now: Instant,
): SessionWindow {
    val start = hours.first().start
    val end = hours.last().start + FORECAST_STEP
    val leaveBy = start - spot.drive - RIG_TIME
    val usableStart = if (now > leaveBy) now + spot.drive + RIG_TIME else start
    val usable = (end - usableStart).coerceAtLeast(Duration.ZERO)
    val marginalHours = hours.count { it.verdict == Verdict.MARGINAL }
    val confidence = confidenceOf(hours)
    return SessionWindow(
        spotId = spot.id,
        start = start,
        end = end,
        hours = hours.size,
        marginalHours = marginalHours,
        leaveBy = leaveBy,
        usableStart = usableStart,
        usable = usable,
        confidence = confidence,
        score = windowScore(usable, marginalHours, confidence),
        recommendedKiteM2 = mostFrequentKite(hours),
        qualifies = usable >= profile.minSession,
    )
}

/** The kite recommended for most hours; the smaller one on a tie; null if no hour has one. */
internal fun mostFrequentKite(hours: List<HourVerdict>): Double? {
    val counts = hours.mapNotNull { it.recommendedKiteM2 }.groupingBy { it }.eachCount()
    val top = counts.values.maxOrNull() ?: return null
    return counts.filterValues { it == top }.keys.min()
}
