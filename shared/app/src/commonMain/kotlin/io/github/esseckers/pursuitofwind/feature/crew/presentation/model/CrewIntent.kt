package io.github.esseckers.pursuitofwind.feature.crew.presentation.model

import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate

/** What the rider can do on the Crew tab. */
internal sealed interface CrewIntent {
    /** Pull to refresh, or retry after an error. */
    data object Refresh : CrewIntent

    data class ChooseCrew(
        val crewId: String,
    ) : CrewIntent

    data object StartCreate : CrewIntent

    data object StartJoin : CrewIntent

    data object CloseDialog : CrewIntent

    data class CreateCrew(
        val name: String,
    ) : CrewIntent

    data class JoinCrew(
        val inviteCode: String,
    ) : CrewIntent

    /** Send the crew's invite code through the system share sheet. */
    data object ShareInvite : CrewIntent

    data class TakeSeat(
        val rideId: String,
    ) : CrewIntent

    data class LeaveRide(
        val rideId: String,
    ) : CrewIntent

    /** Asks the driver to confirm first ([ConfirmCancel]). */
    data class CancelRide(
        val rideId: String,
    ) : CrewIntent

    data object ConfirmCancel : CrewIntent

    /** Show the forecast of [spotId] at [date], local to the spot. */
    data class OpenSpot(
        val spotId: SpotId,
        val date: LocalDate,
    ) : CrewIntent
}

/** The ride this intent acts on and the action it asks for; null for intents that are not about one ride. */
internal fun CrewIntent.rideAction(): Pair<String, RideActionEntity>? =
    when (this) {
        is CrewIntent.TakeSeat -> rideId to RideActionEntity.TAKE_SEAT
        is CrewIntent.LeaveRide -> rideId to RideActionEntity.LEAVE
        is CrewIntent.CancelRide -> rideId to RideActionEntity.CANCEL
        else -> null
    }
