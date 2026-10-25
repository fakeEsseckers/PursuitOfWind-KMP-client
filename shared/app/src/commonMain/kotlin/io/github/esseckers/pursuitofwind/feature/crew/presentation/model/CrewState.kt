package io.github.esseckers.pursuitofwind.feature.crew.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/**
 * What the Crew tab shows: the crew on screen, the [crews] to switch between, a [notice] above cached content when
 * the refresh failed, and the open [dialog]. [inputs] is what it was computed from.
 */
internal data class CrewState(
    val content: Content = Content.Loading,
    val crews: List<CrewChoice> = emptyList(),
    val notice: RefreshNotice? = null,
    val isRefreshing: Boolean = false,
    val dialog: CrewDialog? = null,
    val inputs: CrewInputs = CrewInputs(),
) {
    sealed interface Content {
        data object Loading : Content

        /** The rider belongs to no crew yet: create one or join with a code. */
        data object NoCrew : Content

        /** Nothing cached and the refresh failed with [message]. */
        data class Error(
            val message: StringResource,
        ) : Content

        data class Ready(
            val crew: CrewUi,
        ) : Content
    }
}

/** The ride [rideId] as the crew on screen shows it, if it does. */
internal fun CrewState.ride(rideId: String): CrewRideUi? =
    (content as? CrewState.Content.Ready)
        ?.crew
        ?.days
        ?.firstNotNullOfOrNull { day -> day.rides.firstOrNull { it.id == rideId } }

/** A crew in the switcher. */
internal data class CrewChoice(
    val id: String,
    val name: String,
    val selected: Boolean,
)

/** The crew on screen: who is in it, its invite code, and its rides by day. */
internal data class CrewUi(
    val id: String,
    val name: String,
    val inviteCode: String,
    val members: List<MemberUi>,
    val days: List<RideDayUi>,
)

internal data class MemberUi(
    val name: String,
    val you: Boolean,
    val owner: Boolean,
)

internal data class PersonUi(
    val name: String,
    val you: Boolean,
)

/** The rides whose window starts on [date] at their spot. */
internal data class RideDayUi(
    val date: LocalDate,
    val label: DayLabel,
    val rides: List<CrewRideUi>,
)

/**
 * A ride as the tab shows it: the window local to the spot, the departure in the rider's time zone, and the one
 * [action] the rider can take on it, if any. [pending] while a change made here waits for the server.
 */
internal data class CrewRideUi(
    val id: String,
    val spotId: SpotId,
    val spotName: String,
    val date: LocalDate,
    val window: TimeRange,
    val departAt: String,
    val departed: Boolean,
    val meetingPoint: String,
    val driverName: String,
    val youDrive: Boolean,
    val passengers: List<PersonUi>,
    val seatsFree: Int,
    val seatsTotal: Int,
    val cancelled: Boolean,
    val pending: Boolean,
    val action: RideAction?,
)

internal enum class RideAction {
    TAKE_SEAT,
    LEAVE,
    CANCEL,
}
