package io.github.esseckers.pursuitofwind.core.ui

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** "Today", "Tomorrow" or a weekday; [dayLabel] turns it into text. */
internal sealed interface DayLabel {
    data object Today : DayLabel

    data object Tomorrow : DayLabel

    data class Weekday(
        val day: DayOfWeek,
    ) : DayLabel
}

/** How [date] is named as seen from [today]: days after tomorrow by their weekday. */
internal fun dayLabelOf(
    date: LocalDate,
    today: LocalDate,
): DayLabel =
    when (date) {
        today -> DayLabel.Today
        today.plus(DatePeriod(days = 1)) -> DayLabel.Tomorrow
        else -> DayLabel.Weekday(date.dayOfWeek)
    }

/** Local wall-clock times such as `13:00` and `17:00`. */
internal data class TimeRange(
    val start: String,
    val end: String,
) {
    override fun toString(): String = "$start–$end"
}
