package io.github.esseckers.pursuitofwind.feature.alerts.presentation.mapper

import io.github.esseckers.pursuitofwind.core.link.AppLink
import io.github.esseckers.pursuitofwind.core.platform.LocalNotification
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.alert_title
import io.github.esseckers.pursuitofwind.core.resources.decimal_separator
import io.github.esseckers.pursuitofwind.core.resources.today_kite
import io.github.esseckers.pursuitofwind.core.resources.today_leave_by
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.dayLabelText
import io.github.esseckers.pursuitofwind.core.ui.formatKiteSize
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

/**
 * The words of a notification about a GO day, the same facts as its card on Today: the day and the spot, then when the
 * rider can be on the water, the kite to take and when to leave. A tap opens that spot's forecast at that day. The
 * session is in the spot's local time, because that is where it happens; the day's name and the time to leave are in
 * [riderZone], where the rider is. The time to leave is always a time, never "leave now": a notification may be read
 * hours after it was shown.
 */
internal object AlertTextMapper {
    private const val SEPARATOR = " · "

    suspend fun notification(
        day: DayDecision.Go,
        spot: SpotConfig,
        now: Instant,
        riderZone: TimeZone,
    ): LocalNotification {
        val window = day.window
        val today = now.toLocalDateTime(riderZone).date
        val separator = getString(Res.string.decimal_separator).first()
        val details =
            listOfNotNull(
                TimeRange(window.usableStart.clockTime(spot.timeZone), window.end.clockTime(spot.timeZone)).toString(),
                day.recommendedKiteM2?.let { getString(Res.string.today_kite, formatKiteSize(it, separator)) },
                getString(Res.string.today_leave_by, day.leaveBy.clockTime(riderZone)),
            )
        return LocalNotification(
            id = "window:${day.spotId.value}:${day.date}",
            title = getString(Res.string.alert_title, dayLabelText(dayLabelOf(day.date, today)), spot.name),
            text = details.joinToString(SEPARATOR),
            link = AppLink.Spot(day.spotId.value, day.date).uri,
        )
    }
}
