package io.github.esseckers.pursuitofwind.feature.crew.presentation.mapper

import io.github.esseckers.pursuitofwind.core.domain.entity.RideRoleEntity
import io.github.esseckers.pursuitofwind.core.domain.mapper.RideRulesMapper
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNoticeMapper
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.MemberRole
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.feature.crew.domain.entity.CrewDetailsEntity
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewChoice
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewInputs
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewRideUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState.Content
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.MemberUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.PersonUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.RideDayUi
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.shownCrew
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Pure rules of the Crew tab: what it shows for its inputs. Times of day are local to the spot, departures to the rider. */
internal object CrewStateMapper {
    fun map(
        inputs: CrewInputs,
        riderZone: TimeZone,
    ): CrewState {
        val refresh = inputs.refresh
        val crews = inputs.crews?.value.orEmpty()
        val shown = inputs.shownCrew()
        val base =
            CrewState(
                crews = crews.map { CrewChoice(it.id, it.name, selected = it.id == inputs.shown) },
                isRefreshing = refresh.refreshing,
                dialog = inputs.dialog,
                inputs = inputs,
            )
        val error = refresh.shownError
        val content = content(inputs, shown, error, riderZone)
        return base.copy(
            content = content,
            // Cached content wins over an error: it stays, with a notice above it.
            notice = if (content is Content.Ready) RefreshNoticeMapper.notice(error) else null,
        )
    }

    private fun content(
        inputs: CrewInputs,
        shown: Crew?,
        error: AppError?,
        riderZone: TimeZone,
    ): Content {
        val cached = inputs.crews ?: return Content.Loading
        val details = inputs.details?.takeIf { it.crewId == shown?.id }
        return when {
            shown != null && details != null -> Content.Ready(crewUi(shown, details, inputs, riderZone))

            // The crew is known, its members and rides are still being read; or it is new and not in the list yet.
            shown != null || inputs.shown != null -> Content.Loading

            error != null && cached.updatedAt == null -> Content.Error(error.message())

            inputs.refresh.finished || cached.updatedAt != null -> Content.NoCrew

            else -> Content.Loading
        }
    }

    private fun crewUi(
        crew: Crew,
        details: CrewDetailsEntity,
        inputs: CrewInputs,
        riderZone: TimeZone,
    ): CrewUi {
        val you = details.userId
        val members =
            details.members.value
                // The owner first, then by when they joined, as the server lists them.
                .sortedBy { it.role != MemberRole.OWNER }
                .map { MemberUi(it.displayName, you = it.userId == you, owner = it.role == MemberRole.OWNER) }
        return CrewUi(crew.id, crew.name, crew.inviteCode, members, days(details, inputs, riderZone))
    }

    /** Rides that have not ended, by the day their window starts at their spot, each day by departure. */
    private fun days(
        details: CrewDetailsEntity,
        inputs: CrewInputs,
        riderZone: TimeZone,
    ): List<RideDayUi> {
        val now = inputs.now
        val today = now?.toLocalDateTime(riderZone)?.date
        return details.rides.value
            .filter { now == null || !RideRulesMapper.hasEnded(it, now) }
            .sortedBy { it.departAt }
            .map { ride -> rideUi(ride, details, inputs, riderZone) }
            .groupBy { it.date }
            .entries
            .sortedBy { it.key }
            .map { (date, rides) ->
                RideDayUi(
                    date,
                    if (today ==
                        null
                    ) {
                        DayLabel.Weekday(date.dayOfWeek)
                    } else {
                        dayLabelOf(date, today)
                    },
                    rides,
                )
            }
    }

    private fun rideUi(
        ride: Ride,
        details: CrewDetailsEntity,
        inputs: CrewInputs,
        riderZone: TimeZone,
    ): CrewRideUi {
        val spot = details.spots[ride.spotId]
        val zone = spot?.timeZone ?: riderZone
        val you = details.userId
        val now = inputs.now
        return CrewRideUi(
            id = ride.id,
            spotId = ride.spotId,
            spotName = spot?.name ?: ride.spotId.value,
            date = ride.windowStart.toLocalDateTime(zone).date,
            window = TimeRange(ride.windowStart.clockTime(zone), ride.windowEnd.clockTime(zone)),
            departAt = ride.departAt.clockTime(riderZone),
            departed = now != null && RideRulesMapper.hasDeparted(ride, now),
            meetingPoint = ride.meetingPoint,
            driverName = ride.driver.displayName,
            youDrive = RideRulesMapper.role(ride, you) == RideRoleEntity.DRIVER,
            passengers = ride.passengers.map { PersonUi(it.displayName, you = it.userId == you) },
            seatsFree = ride.seatsFree,
            seatsTotal = ride.seatsTotal,
            cancelled = ride.status == RideStatus.CANCELLED,
            pending = ride.pending || ride.id in inputs.cancelling,
            action = RideRulesMapper.action(ride, you, now),
        )
    }
}
