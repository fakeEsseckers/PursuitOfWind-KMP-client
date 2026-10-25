package io.github.esseckers.pursuitofwind.feature.today.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.esseckers.pursuitofwind.core.designsystem.component.ConfidenceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.LinkRow
import io.github.esseckers.pursuitofwind.core.designsystem.component.VerdictBadge
import io.github.esseckers.pursuitofwind.core.designsystem.component.VerdictCard
import io.github.esseckers.pursuitofwind.core.designsystem.component.verdictPalette
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.domain.entity.RideRoleEntity
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.confidence_low_hint
import io.github.esseckers.pursuitofwind.core.resources.confidence_unknown_hint
import io.github.esseckers.pursuitofwind.core.resources.decimal_separator
import io.github.esseckers.pursuitofwind.core.resources.open_spot
import io.github.esseckers.pursuitofwind.core.resources.ride_departed
import io.github.esseckers.pursuitofwind.core.resources.ride_driver
import io.github.esseckers.pursuitofwind.core.resources.ride_full
import io.github.esseckers.pursuitofwind.core.resources.ride_pending
import io.github.esseckers.pursuitofwind.core.resources.ride_seats_free
import io.github.esseckers.pursuitofwind.core.resources.ride_you_departed
import io.github.esseckers.pursuitofwind.core.resources.ride_you_drive
import io.github.esseckers.pursuitofwind.core.resources.ride_you_ride
import io.github.esseckers.pursuitofwind.core.resources.today_alternative
import io.github.esseckers.pursuitofwind.core.resources.today_im_going
import io.github.esseckers.pursuitofwind.core.resources.today_im_going_description
import io.github.esseckers.pursuitofwind.core.resources.today_kite
import io.github.esseckers.pursuitofwind.core.resources.today_leave_by
import io.github.esseckers.pursuitofwind.core.resources.today_leave_now
import io.github.esseckers.pursuitofwind.core.resources.today_next_go
import io.github.esseckers.pursuitofwind.core.resources.today_no_next_go
import io.github.esseckers.pursuitofwind.core.resources.today_no_rides
import io.github.esseckers.pursuitofwind.core.resources.today_spot_reason
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.core.ui.formatKiteSize
import io.github.esseckers.pursuitofwind.core.ui.reasonLabel
import io.github.esseckers.pursuitofwind.core.ui.windRangeText
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideOfferUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideUi
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The answer for one day. Screen readers read the whole card as one item, and can jump between days as headings.
 * [onOpenSpot] opens the hourly forecast of a spot: tapping a GO card opens its spot, the spots listed on a card open
 * their own. A GO card with a crew offers "I'm going" ([onOfferRide]).
 */
@Composable
internal fun DayCard(
    day: DayUi,
    onOpenSpot: (SpotId) -> Unit,
    modifier: Modifier = Modifier,
    onOfferRide: (RideOfferUi) -> Unit = {},
) {
    val cardModifier =
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .testTag("today.day.${day.date}")
    when (day) {
        is DayUi.Go -> GoCard(day, onOpenSpot, onOfferRide, cardModifier)
        is DayUi.NoGo -> NoGoCard(day, onOpenSpot, cardModifier)
    }
}

@Composable
private fun GoCard(
    day: DayUi.Go,
    onOpenSpot: (SpotId) -> Unit,
    onOfferRide: (RideOfferUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    VerdictCard(
        verdict = Verdict.GO,
        modifier = modifier,
        onClick = { onOpenSpot(day.spotId) },
        onClickLabel = stringResource(Res.string.open_spot, day.spotName),
    ) {
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
            Rides(day, onOfferRide)
            day.alternatives.forEach { alternative ->
                LinkRow(
                    text = stringResource(Res.string.today_alternative, alternative.spotName, alternative.window.toString()),
                    onClickLabel = stringResource(Res.string.open_spot, alternative.spotName),
                    onClick = { onOpenSpot(alternative.spotId) },
                    modifier = Modifier.testTag("today.alternative.${alternative.spotId.value}"),
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
private fun Rides(
    day: DayUi.Go,
    onOfferRide: (RideOfferUi) -> Unit,
) {
    if (day.rides.isEmpty() && day.hasCrew) {
        IconLine(icon = AppIcons.Car, text = stringResource(Res.string.today_no_rides), style = MaterialTheme.typography.bodyMedium)
    }
    day.rides.forEach { ride -> RideLine(ride) }
    day.offer?.let { offer ->
        val description = stringResource(Res.string.today_im_going_description, day.spotName)
        FilledTonalButton(
            onClick = { onOfferRide(offer) },
            modifier = Modifier.semantics { contentDescription = description }.testTag("today.offer.${day.date}"),
        ) {
            Text(text = stringResource(Res.string.today_im_going), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RideLine(ride: RideUi) {
    Column(modifier = Modifier.testTag("today.ride.${ride.id}")) {
        val driver =
            when {
                ride.role == RideRoleEntity.DRIVER && ride.departed -> stringResource(Res.string.ride_you_departed, ride.departAt)
                ride.role == RideRoleEntity.DRIVER -> stringResource(Res.string.ride_you_drive, ride.departAt)
                ride.departed -> stringResource(Res.string.ride_departed, ride.driverName, ride.departAt)
                else -> stringResource(Res.string.ride_driver, ride.driverName, ride.departAt)
            }
        IconLine(icon = AppIcons.Car, text = driver, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        // Seats no longer matter once the car has left.
        val seats =
            when {
                ride.departed -> null
                ride.pending -> stringResource(Res.string.ride_pending)
                ride.role == RideRoleEntity.PASSENGER -> stringResource(Res.string.ride_you_ride)
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

/** With one favorite spot the whole card opens it; with several, each spot's line opens that spot. */
@Composable
private fun NoGoCard(
    day: DayUi.NoGo,
    onOpenSpot: (SpotId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val single = day.spots.singleOrNull()
    VerdictCard(
        verdict = Verdict.NO_GO,
        modifier = modifier,
        onClick = single?.let { { onOpenSpot(it.spotId) } },
        onClickLabel = single?.let { stringResource(Res.string.open_spot, it.spotName) },
    ) {
        CardHeader(verdict = Verdict.NO_GO, label = day.label)
        Text(text = stringResource(reasonLabel(day.reason)), style = MaterialTheme.typography.titleMedium)
        // One spot's reason is the main reason; listing it again would add nothing.
        if (day.spots.size > 1) {
            Column {
                day.spots.forEach { spot ->
                    LinkRow(
                        text = stringResource(Res.string.today_spot_reason, spot.spotName, stringResource(reasonLabel(spot.reason))),
                        onClickLabel = stringResource(Res.string.open_spot, spot.spotName),
                        onClick = { onOpenSpot(spot.spotId) },
                        modifier = Modifier.testTag("today.spot.${day.date}.${spot.spotId.value}"),
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
