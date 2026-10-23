package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.Reason
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

// UI models of the Today screen: formatted for display, with no domain logic left to run in composables.

/** The answer for one day, formatted for display: times are local to the spot, wind in the rider's unit. */
internal sealed interface DayUi {
    val date: LocalDate
    val label: DayLabel

    data class Go(
        override val date: LocalDate,
        override val label: DayLabel,
        val spotName: String,
        /** When the rider can ride: the window, starting later if the time to leave has passed. */
        val window: TimeRange,
        val wind: WindRange?,
        val kiteM2: Double?,
        val leaveBy: String,
        val leaveNow: Boolean,
        val confidence: Confidence,
        val rides: List<RideUi>,
        val hasCrew: Boolean,
        val alternatives: List<AlternativeUi>,
    ) : DayUi

    data class NoGo(
        override val date: LocalDate,
        override val label: DayLabel,
        val reason: Reason,
        val spots: List<SpotReasonUi>,
        val nextGoDay: DayLabel?,
    ) : DayUi
}

/** "Today", "Tomorrow" or a weekday. */
internal sealed interface DayLabel {
    data object Today : DayLabel

    data object Tomorrow : DayLabel

    data class Weekday(
        val day: DayOfWeek,
    ) : DayLabel
}

/** Local wall-clock times such as `13:00` and `17:00`. */
internal data class TimeRange(
    val start: String,
    val end: String,
) {
    override fun toString(): String = "$start–$end"
}

/** Mean wind across a window, rounded, in [unit]. */
internal data class WindRange(
    val low: Int,
    val high: Int,
    val unit: WindUnit,
)

internal data class RideUi(
    val id: String,
    val driverName: String,
    val departAt: String,
    val departed: Boolean,
    val seatsFree: Int,
    val youDrive: Boolean,
    val youRide: Boolean,
    val pending: Boolean,
)

internal data class AlternativeUi(
    val spotName: String,
    val window: TimeRange,
)

internal data class SpotReasonUi(
    val spotName: String,
    val reason: Reason,
)
