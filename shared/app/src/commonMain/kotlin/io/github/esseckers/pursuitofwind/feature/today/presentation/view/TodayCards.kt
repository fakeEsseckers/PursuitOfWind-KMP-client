package io.github.esseckers.pursuitofwind.feature.today.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.esseckers.pursuitofwind.core.designsystem.component.ConfidenceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.VerdictBadge
import io.github.esseckers.pursuitofwind.core.designsystem.component.VerdictCard
import io.github.esseckers.pursuitofwind.core.designsystem.component.verdictPalette
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.confidence_low_hint
import io.github.esseckers.pursuitofwind.core.resources.confidence_unknown_hint
import io.github.esseckers.pursuitofwind.core.resources.decimal_separator
import io.github.esseckers.pursuitofwind.core.resources.ride_departed
import io.github.esseckers.pursuitofwind.core.resources.ride_driver
import io.github.esseckers.pursuitofwind.core.resources.ride_full
import io.github.esseckers.pursuitofwind.core.resources.ride_pending
import io.github.esseckers.pursuitofwind.core.resources.ride_seats_free
import io.github.esseckers.pursuitofwind.core.resources.ride_you_departed
import io.github.esseckers.pursuitofwind.core.resources.ride_you_drive
import io.github.esseckers.pursuitofwind.core.resources.ride_you_ride
import io.github.esseckers.pursuitofwind.core.resources.today_alternative
import io.github.esseckers.pursuitofwind.core.resources.today_kite
import io.github.esseckers.pursuitofwind.core.resources.today_leave_by
import io.github.esseckers.pursuitofwind.core.resources.today_leave_now
import io.github.esseckers.pursuitofwind.core.resources.today_next_go
import io.github.esseckers.pursuitofwind.core.resources.today_no_next_go
import io.github.esseckers.pursuitofwind.core.resources.today_no_rides
import io.github.esseckers.pursuitofwind.core.resources.today_spot_reason
import io.github.esseckers.pursuitofwind.core.ui.formatKiteSize
import io.github.esseckers.pursuitofwind.core.ui.reasonLabel
import io.github.esseckers.pursuitofwind.core.ui.windRangeText
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayLabel
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideUi
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The answer for one day. Screen readers read the whole card as one item, and can jump between days as headings.
 */
@Composable
internal fun DayCard(
    day: DayUi,
    modifier: Modifier = Modifier,
) {
    val cardModifier =
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .testTag("today.day.${day.date}")
    when (day) {
        is DayUi.Go -> GoCard(day, cardModifier)
        is DayUi.NoGo -> NoGoCard(day, cardModifier)
    }
}

@Composable
private fun GoCard(
    day: DayUi.Go,
    modifier: Modifier = Modifier,
) {
    VerdictCard(verdict = Verdict.GO, modifier = modifier) {
        CardHeader(verdict = Verdict.GO, label = day.label)
        Column {
            Text(text = day.spotName, style = MaterialTheme.typography.titleLarge)
            Text(text = day.window.toString(), style = MaterialTheme.typography.headlineSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            day.wind?.let { IconLine(icon = AppIcons.Wind, text = windRangeText(it.low, it.high, it.unit)) }
            day.kiteM2?.let { size ->
                val kite = formatKiteSize(size, decimalSeparator = stringResource(Res.string.decimal_separator).single())
                IconLine(icon = AppIcons.Kite, text = stringResource(Res.string.today_kite, kite))
            }
        }
        IconLine(
            icon = AppIcons.Clock,
            text = if (day.leaveNow) stringResource(Res.string.today_leave_now) else stringResource(Res.string.today_leave_by, day.leaveBy),
            style = MaterialTheme.typography.titleMedium,
        )
        ConfidenceChip(confidence = day.confidence)
        confidenceHint(day.confidence)?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        if (day.rides.isNotEmpty() || day.hasCrew || day.alternatives.isNotEmpty()) {
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = DIVIDER_ALPHA))
            Rides(day)
            day.alternatives.forEach { alternative ->
                Text(
                    text = stringResource(Res.string.today_alternative, alternative.spotName, alternative.window.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun confidenceHint(confidence: Confidence): String? =
    when (confidence) {
        Confidence.LOW -> stringResource(Res.string.confidence_low_hint)
        Confidence.UNKNOWN -> stringResource(Res.string.confidence_unknown_hint)
        Confidence.HIGH, Confidence.MEDIUM -> null
    }

@Composable
private fun Rides(day: DayUi.Go) {
    if (day.rides.isEmpty() && day.hasCrew) {
        IconLine(icon = AppIcons.Car, text = stringResource(Res.string.today_no_rides), style = MaterialTheme.typography.bodyMedium)
    }
    day.rides.forEach { ride -> RideLine(ride) }
}

@Composable
private fun RideLine(ride: RideUi) {
    Column(modifier = Modifier.testTag("today.ride.${ride.id}")) {
        val driver =
            when {
                ride.youDrive && ride.departed -> stringResource(Res.string.ride_you_departed, ride.departAt)
                ride.youDrive -> stringResource(Res.string.ride_you_drive, ride.departAt)
                ride.departed -> stringResource(Res.string.ride_departed, ride.driverName, ride.departAt)
                else -> stringResource(Res.string.ride_driver, ride.driverName, ride.departAt)
            }
        IconLine(icon = AppIcons.Car, text = driver, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        // Seats no longer matter once the car has left.
        val seats =
            when {
                ride.departed -> null
                ride.pending -> stringResource(Res.string.ride_pending)
                ride.youRide -> stringResource(Res.string.ride_you_ride)
                ride.seatsFree == 0 -> stringResource(Res.string.ride_full)
                else -> pluralStringResource(Res.plurals.ride_seats_free, ride.seatsFree, ride.seatsFree)
            }
        seats?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Sizes.icon + Spacing.s),
            )
        }
    }
}

@Composable
private fun NoGoCard(
    day: DayUi.NoGo,
    modifier: Modifier = Modifier,
) {
    VerdictCard(verdict = Verdict.NO_GO, modifier = modifier) {
        CardHeader(verdict = Verdict.NO_GO, label = day.label)
        Text(text = stringResource(reasonLabel(day.reason)), style = MaterialTheme.typography.titleMedium)
        if (day.spots.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                day.spots.forEach { spot ->
                    Text(
                        text = stringResource(Res.string.today_spot_reason, spot.spotName, stringResource(reasonLabel(spot.reason))),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Text(
            text =
                day.nextGoDay?.let {
                    stringResource(
                        Res.string.today_next_go,
                        dayLabel(it, inSentence = true),
                    )
                } ?: stringResource(Res.string.today_no_next_go),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = verdictPalette(Verdict.NO_GO).onContainer,
        )
    }
}

/** Verdict on the left, the day on the right; they wrap onto two lines at large font sizes. */
@Composable
private fun CardHeader(
    verdict: Verdict,
    label: DayLabel,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        VerdictBadge(verdict = verdict)
        Text(text = dayLabel(label), style = MaterialTheme.typography.titleMedium)
    }
}

private const val DIVIDER_ALPHA = 0.2f
