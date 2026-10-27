package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model

import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

// UI models of the spot detail screen. Wind for drawing stays in m/s, so the chart's scale does not depend on the
// unit; wind for reading is rounded in the rider's unit. Times are local to the spot, departures to the rider.

/** The spot above the chart: its name and the drive from home. */
internal data class SpotHeaderUi(
    val name: String,
    val drive: Duration,
)

/**
 * Everything the wind chart draws, one entry per hour from the current hour on.
 *
 * @property scaleMaxMs the wind speed at the top of the chart.
 * @property grid the horizontal lines, from the bottom up.
 */
internal data class WindChartUi(
    val hours: List<ChartHourUi>,
    val windows: List<ChartWindowUi>,
    val grid: List<GridLineUi>,
    val scaleMaxMs: Double,
    val unit: WindUnit,
)

/**
 * One hour on the chart.
 *
 * @property date the day the hour belongs to, local to the spot.
 * @property day [date] as the rider names it: today, tomorrow or a weekday.
 * @property label the local time, such as `14:00`; both hours of a clock change carry their offset.
 * @property showLabel true every third hour, where the time axis has a label.
 * @property firstOfDay true where a new local day starts, or for the first hour.
 * @property spreadLowMs the lowest weather model's mean wind; null when only one model is available.
 * @property spreadHighMs the highest weather model's mean wind; null when only one model is available.
 * @property bearingDeg where the wind comes from, in degrees.
 */
internal data class ChartHourUi(
    val start: Instant,
    val date: LocalDate,
    val day: DayLabel,
    val label: String,
    val showLabel: Boolean,
    val firstOfDay: Boolean,
    val verdict: Verdict,
    val meanMs: Double,
    val gustMs: Double,
    val spreadLowMs: Double?,
    val spreadHighMs: Double?,
    val bearingDeg: Double,
    val daylight: Boolean,
    val mean: Int,
    val gust: Int,
)

/** A session window on the chart, from hour [first] to hour [last] inclusive; [qualifies] is false when too short. */
internal data class ChartWindowUi(
    val first: Int,
    val last: Int,
    val qualifies: Boolean,
)

/** A horizontal line of the chart at [ms], labeled [value] in the chart's unit. */
internal data class GridLineUi(
    val ms: Double,
    val value: Int,
)

/** A day on the chart, for the day chips: [firstHour] is the index of its first hour. */
internal data class ChartDayUi(
    val date: LocalDate,
    val label: DayLabel,
    val firstHour: Int,
)

/**
 * A session window, as listed under the chart.
 *
 * @property firstHour the index of its first hour on the chart.
 * @property time when the rider can ride: from the window's start, or later once the time to leave has passed.
 * @property windLow the lowest mean wind of the riding time, in [unit].
 * @property windHigh the highest mean wind of the riding time, in [unit].
 * @property leaveBy the latest departure, in the rider's time zone.
 * @property qualifies false when the window is shorter than the rider's minimum session after the drive.
 */
internal data class WindowUi(
    val start: Instant,
    val firstHour: Int,
    val date: LocalDate,
    val day: DayLabel,
    val time: TimeRange,
    val windLow: Int,
    val windHigh: Int,
    val unit: WindUnit,
    val kiteM2: Double?,
    val leaveBy: String,
    val leaveNow: Boolean,
    val confidence: Confidence,
    val qualifies: Boolean,
)

/**
 * The hour the rider tapped, in words: everything the bar shows, and why it is not a GO.
 *
 * @property index the hour's index on the chart.
 * @property reasons every non-GO reason, most severe first; empty for GO.
 * @property models each weather model's mean wind, in [unit]; one entry when only one model is available.
 */
internal data class HourDetailsUi(
    val index: Int,
    val start: Instant,
    val day: DayLabel,
    val time: String,
    val verdict: Verdict,
    val reasons: List<Reason>,
    val mean: Int,
    val gust: Int,
    val unit: WindUnit,
    val bearingDeg: Int,
    val compass: Compass,
    val models: List<ModelWindUi>,
    val kiteM2: Double?,
    val tempC: Int,
    val daylight: Boolean,
)

/** One weather model's mean wind for an hour, such as ECMWF 18. */
internal data class ModelWindUi(
    val model: String,
    val mean: Int,
)

/** The eight main compass points the wind comes from. */
internal enum class Compass {
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    NW,
    ;

    companion object {
        private const val SECTOR_DEG = 45.0
        private const val FULL_CIRCLE_DEG = 360.0

        /** The compass point nearest to [bearingDeg]; each covers 45° around its direction. */
        fun of(bearingDeg: Double): Compass {
            val normalized = ((bearingDeg % FULL_CIRCLE_DEG) + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
            return entries[((normalized + SECTOR_DEG / 2) / SECTOR_DEG).toInt() % entries.size]
        }
    }
}
