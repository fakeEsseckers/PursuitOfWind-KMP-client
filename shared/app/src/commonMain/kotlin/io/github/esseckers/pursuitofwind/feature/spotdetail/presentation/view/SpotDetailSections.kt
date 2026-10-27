package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ConfidenceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.VerdictBadge
import io.github.esseckers.pursuitofwind.core.designsystem.component.VerdictCard
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.decimal_separator
import io.github.esseckers.pursuitofwind.core.resources.details_all_good
import io.github.esseckers.pursuitofwind.core.resources.details_direction
import io.github.esseckers.pursuitofwind.core.resources.details_models
import io.github.esseckers.pursuitofwind.core.resources.details_temperature
import io.github.esseckers.pursuitofwind.core.resources.details_wind
import io.github.esseckers.pursuitofwind.core.resources.reason_window_too_short
import io.github.esseckers.pursuitofwind.core.resources.today_kite
import io.github.esseckers.pursuitofwind.core.resources.today_leave_by
import io.github.esseckers.pursuitofwind.core.resources.today_leave_now
import io.github.esseckers.pursuitofwind.core.resources.window_day_time
import io.github.esseckers.pursuitofwind.core.resources.window_show
import io.github.esseckers.pursuitofwind.core.resources.windows_none
import io.github.esseckers.pursuitofwind.core.resources.windows_title
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.core.ui.formatKiteSize
import io.github.esseckers.pursuitofwind.core.ui.reasonLabel
import io.github.esseckers.pursuitofwind.core.ui.windRangeText
import io.github.esseckers.pursuitofwind.core.ui.windUnitLabel
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartDayUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.HourDetailsUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.WindowUi
import org.jetbrains.compose.resources.stringResource

/** One chip per day on the chart; the chip of the selected hour's day is on. They wrap at large font sizes. */
@Composable
internal fun DayChips(
    days: List<ChartDayUi>,
    selectedHour: Int,
    onSelectDay: (ChartDayUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = days.lastOrNull { it.firstHour <= selectedHour }
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        days.forEach { day ->
            FilterChip(
                selected = day == current,
                onClick = { onSelectDay(day) },
                label = { Text(dayLabel(day.label)) },
                modifier = Modifier.testTag("spot.day.${day.date}"),
            )
        }
    }
}

/**
 * The selected hour in words, in the colors of its verdict: wind, direction, models, and every reason it is not a GO.
 * Screen readers announce it when the selection changes.
 */
@Composable
internal fun HourDetails(
    details: HourDetailsUi,
    modifier: Modifier = Modifier,
) {
    VerdictCard(
        verdict = details.verdict,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("spot.details"),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            VerdictBadge(verdict = details.verdict)
            Text(text = "${dayLabel(details.day)} ${details.time}", style = MaterialTheme.typography.titleMedium)
        }
        IconLine(
            icon = AppIcons.Wind,
            text = stringResource(Res.string.details_wind, windText(details.mean, details.unit), windText(details.gust, details.unit)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = stringResource(Res.string.details_direction, stringResource(compassWords(details.compass)), details.bearingDeg))
        if (details.verdict != Verdict.NO_GO) {
            details.kiteM2?.let { size ->
                val kite = formatKiteSize(size, decimalSeparator = stringResource(Res.string.decimal_separator).single())
                IconLine(icon = AppIcons.Kite, text = stringResource(Res.string.today_kite, kite))
            }
        }
        if (details.reasons.isEmpty()) {
            Text(text = stringResource(Res.string.details_all_good), style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                details.reasons.forEach { Text(text = "• " + stringResource(reasonLabel(it)), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        val unit = stringResource(windUnitLabel(details.unit))
        val models = details.models.joinToString(" · ") { "${it.model} ${it.mean}" } + " $unit"
        Text(text = stringResource(Res.string.details_models, models), style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(Res.string.details_temperature, details.tempC), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The windows in the forecast, each a button that shows it on the chart. */
@Composable
internal fun WindowList(
    windows: List<WindowUi>,
    onShowWindow: (WindowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag("spot.windows"), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = stringResource(Res.string.windows_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (windows.isEmpty()) {
            Text(text = stringResource(Res.string.windows_none), style = MaterialTheme.typography.bodyLarge)
        }
        val showLabel = stringResource(Res.string.window_show)
        windows.forEach { window ->
            WindowCard(window, onClick = { onShowWindow(window) }, onClickLabel = showLabel)
        }
    }
}

@Composable
private fun WindowCard(
    window: WindowUi,
    onClick: () -> Unit,
    onClickLabel: String,
) {
    val verdict = if (window.qualifies) Verdict.GO else Verdict.MARGINAL
    VerdictCard(
        verdict = verdict,
        modifier = Modifier.fillMaxWidth().testTag("spot.window.${window.firstHour}"),
        onClick = onClick,
        onClickLabel = onClickLabel,
    ) {
        Text(
            text = stringResource(Res.string.window_day_time, dayLabel(window.day), window.time.toString()),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconLine(icon = AppIcons.Wind, text = windRangeText(window.windLow, window.windHigh, window.unit))
            window.kiteM2?.let { size ->
                val kite = formatKiteSize(size, decimalSeparator = stringResource(Res.string.decimal_separator).single())
                IconLine(icon = AppIcons.Kite, text = stringResource(Res.string.today_kite, kite))
            }
        }
        if (window.qualifies) {
            IconLine(
                icon = AppIcons.Clock,
                text =
                    if (window.leaveNow) {
                        stringResource(
                            Res.string.today_leave_now,
                        )
                    } else {
                        stringResource(Res.string.today_leave_by, window.leaveBy)
                    },
            )
            ConfidenceChip(confidence = window.confidence)
        } else {
            IconLine(icon = AppIcons.Marginal, text = stringResource(Res.string.reason_window_too_short))
        }
    }
}
