package io.github.esseckers.pursuitofwind.feature.crew.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.LinkRow
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_cancel_ride
import io.github.esseckers.pursuitofwind.core.resources.action_leave_ride
import io.github.esseckers.pursuitofwind.core.resources.action_take_seat
import io.github.esseckers.pursuitofwind.core.resources.crew_cancel_ride_to
import io.github.esseckers.pursuitofwind.core.resources.crew_leave_ride_to
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_cancelled
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_meeting
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_window
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_with
import io.github.esseckers.pursuitofwind.core.resources.crew_take_seat_to
import io.github.esseckers.pursuitofwind.core.resources.crew_you
import io.github.esseckers.pursuitofwind.core.resources.my_spots_forecast
import io.github.esseckers.pursuitofwind.core.resources.open_spot
import io.github.esseckers.pursuitofwind.core.ui.rideDriverText
import io.github.esseckers.pursuitofwind.core.ui.rideSeatsText
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewRideUi
import org.jetbrains.compose.resources.stringResource

/**
 * One ride of the crew: the spot as a heading, who drives and when, where they meet, the window, who rides along and
 * the seats, the one thing the rider can do on it, and a link to the spot's forecast. A cancelled ride stays, crossed
 * out, until its window ends.
 */
@Composable
internal fun CrewRideCard(
    ride: CrewRideUi,
    onAction: (RideActionEntity) -> Unit,
    onOpenSpot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth().testTag("crew.ride.${ride.id}")) {
        Column(
            modifier = Modifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.m, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            val decoration = if (ride.cancelled) TextDecoration.LineThrough else null
            Text(
                text = ride.spotName,
                style = MaterialTheme.typography.titleMedium.copy(textDecoration = decoration),
                modifier = Modifier.semantics { heading() },
            )
            Column(
                modifier = if (ride.cancelled) Modifier.alpha(CANCELLED_ALPHA) else Modifier,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                IconLine(
                    icon = AppIcons.Car,
                    text = rideDriverText(ride.driverName, ride.youDrive, ride.departed, ride.departAt),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                IconLine(icon = AppIcons.Clock, text = stringResource(Res.string.crew_ride_window, ride.window.toString()))
                IconLine(icon = AppIcons.Pin, text = stringResource(Res.string.crew_ride_meeting, ride.meetingPoint))
                if (ride.passengers.isNotEmpty()) IconLine(icon = AppIcons.Person, text = passengersText(ride))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    text = seatsText(ride),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("crew.seats.${ride.id}"),
                )
                ride.action?.let { action -> RideActionButton(ride, action, onClick = { onAction(action) }) }
            }
            LinkRow(
                text = stringResource(Res.string.my_spots_forecast),
                onClickLabel = stringResource(Res.string.open_spot, ride.spotName),
                onClick = onOpenSpot,
                modifier = Modifier.testTag("crew.open.${ride.id}"),
            )
        }
    }
}

@Composable
private fun passengersText(ride: CrewRideUi): String {
    val you = stringResource(Res.string.crew_you)
    return stringResource(Res.string.crew_ride_with, ride.passengers.joinToString(", ") { if (it.you) you else it.name })
}

@Composable
private fun seatsText(ride: CrewRideUi): String =
    if (ride.cancelled) stringResource(Res.string.crew_ride_cancelled) else rideSeatsText(ride.seatsFree, ride.pending)

/** The ride's one action; named after the spot for screen readers, since every card has one. Busy while [CrewRideUi.pending]. */
@Composable
private fun RideActionButton(
    ride: CrewRideUi,
    action: RideActionEntity,
    onClick: () -> Unit,
) {
    val description =
        when (action) {
            RideActionEntity.TAKE_SEAT -> stringResource(Res.string.crew_take_seat_to, ride.spotName)
            RideActionEntity.LEAVE -> stringResource(Res.string.crew_leave_ride_to, ride.spotName)
            RideActionEntity.CANCEL -> stringResource(Res.string.crew_cancel_ride_to, ride.spotName)
        }
    val modifier = Modifier.semantics { contentDescription = description }.testTag("crew.action.${ride.id}")
    val enabled = !ride.pending
    val content: @Composable () -> Unit = {
        if (ride.pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.iconSmall),
                color = LocalContentColor.current,
                strokeWidth = Sizes.progressStroke,
            )
        } else {
            Text(text = actionText(action), style = MaterialTheme.typography.labelLarge)
        }
    }
    when (action) {
        RideActionEntity.TAKE_SEAT -> FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
        RideActionEntity.LEAVE -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
        RideActionEntity.CANCEL -> TextButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    }
}

@Composable
private fun actionText(action: RideActionEntity): String =
    when (action) {
        RideActionEntity.TAKE_SEAT -> stringResource(Res.string.action_take_seat)
        RideActionEntity.LEAVE -> stringResource(Res.string.action_leave_ride)
        RideActionEntity.CANCEL -> stringResource(Res.string.action_cancel_ride)
    }

private const val CANCELLED_ALPHA = 0.6f
