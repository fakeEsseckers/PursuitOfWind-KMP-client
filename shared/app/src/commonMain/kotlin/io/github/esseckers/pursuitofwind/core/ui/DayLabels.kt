package io.github.esseckers.pursuitofwind.core.ui

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.day_today
import io.github.esseckers.pursuitofwind.core.resources.day_today_inline
import io.github.esseckers.pursuitofwind.core.resources.day_tomorrow
import io.github.esseckers.pursuitofwind.core.resources.day_tomorrow_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_friday
import io.github.esseckers.pursuitofwind.core.resources.weekday_friday_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_monday
import io.github.esseckers.pursuitofwind.core.resources.weekday_monday_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_saturday
import io.github.esseckers.pursuitofwind.core.resources.weekday_saturday_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_sunday
import io.github.esseckers.pursuitofwind.core.resources.weekday_sunday_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_thursday
import io.github.esseckers.pursuitofwind.core.resources.weekday_thursday_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_tuesday
import io.github.esseckers.pursuitofwind.core.resources.weekday_tuesday_inline
import io.github.esseckers.pursuitofwind.core.resources.weekday_wednesday
import io.github.esseckers.pursuitofwind.core.resources.weekday_wednesday_inline
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Today", "Tomorrow" or the weekday. [inSentence] gives the form used inside a sentence ("Next good day: tomorrow"),
 * which differs in languages such as Russian, where weekdays are not capitalized.
 */
@Composable
internal fun dayLabel(
    label: DayLabel,
    inSentence: Boolean = false,
): String {
    val (standalone, inline) =
        when (label) {
            DayLabel.Today -> Res.string.day_today to Res.string.day_today_inline
            DayLabel.Tomorrow -> Res.string.day_tomorrow to Res.string.day_tomorrow_inline
            is DayLabel.Weekday -> weekday(label.day)
        }
    return stringResource(if (inSentence) inline else standalone)
}

private fun weekday(day: DayOfWeek): Pair<StringResource, StringResource> =
    when (day) {
        DayOfWeek.MONDAY -> Res.string.weekday_monday to Res.string.weekday_monday_inline
        DayOfWeek.TUESDAY -> Res.string.weekday_tuesday to Res.string.weekday_tuesday_inline
        DayOfWeek.WEDNESDAY -> Res.string.weekday_wednesday to Res.string.weekday_wednesday_inline
        DayOfWeek.THURSDAY -> Res.string.weekday_thursday to Res.string.weekday_thursday_inline
        DayOfWeek.FRIDAY -> Res.string.weekday_friday to Res.string.weekday_friday_inline
        DayOfWeek.SATURDAY -> Res.string.weekday_saturday to Res.string.weekday_saturday_inline
        DayOfWeek.SUNDAY -> Res.string.weekday_sunday to Res.string.weekday_sunday_inline
    }
