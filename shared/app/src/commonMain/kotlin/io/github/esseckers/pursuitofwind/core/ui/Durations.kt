package io.github.esseckers.pursuitofwind.core.ui

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.drive_hours
import io.github.esseckers.pursuitofwind.core.resources.drive_hours_minutes
import io.github.esseckers.pursuitofwind.core.resources.drive_minutes
import io.github.esseckers.pursuitofwind.core.resources.duration_hours
import io.github.esseckers.pursuitofwind.core.resources.duration_hours_minutes
import io.github.esseckers.pursuitofwind.core.resources.duration_minutes
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/** `2 h`, `2 h 30 min`, `45 min`. */
@Composable
internal fun durationText(duration: Duration): String =
    hoursAndMinutes(duration, Res.string.duration_minutes, Res.plurals.duration_hours, Res.string.duration_hours_minutes)

/** `1 h 30 min drive`, `45 min drive`. */
@Composable
internal fun driveText(drive: Duration): String =
    hoursAndMinutes(drive, Res.string.drive_minutes, Res.plurals.drive_hours, Res.string.drive_hours_minutes)

@Composable
private fun hoursAndMinutes(
    duration: Duration,
    minutesOnly: StringResource,
    hoursOnly: PluralStringResource,
    both: StringResource,
): String {
    val hours = duration.inWholeHours.toInt()
    val minutes = (duration.inWholeMinutes % MINUTES_PER_HOUR).toInt()
    return when {
        hours == 0 -> stringResource(minutesOnly, minutes)
        minutes == 0 -> pluralStringResource(hoursOnly, hours, hours)
        else -> stringResource(both, hours, minutes)
    }
}

private const val MINUTES_PER_HOUR = 60
