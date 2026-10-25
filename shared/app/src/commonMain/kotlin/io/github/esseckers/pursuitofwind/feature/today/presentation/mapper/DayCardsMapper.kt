package io.github.esseckers.pursuitofwind.feature.today.presentation.mapper

import io.github.esseckers.pursuitofwind.core.domain.entity.RideRoleEntity
import io.github.esseckers.pursuitofwind.core.domain.mapper.RideRulesMapper
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.rounded
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.CrewRidesEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.DayEvaluationEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.SpotForecastEntity
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.AlternativeUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideOfferUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.SpotReasonUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.WindRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Formats the engine's days (`TodayEvaluationMapper`) for display, with the crew's rides to each GO day's spot. Session
 * windows are shown in the spot's local time, because that is where they happen; departures and the "today" label use
 * [riderZone], where the rider is.
 */
internal class DayCardsMapper(
    private val profile: Profile,
    spots: List<SpotForecastEntity>,
    private val crew: CrewRidesEntity,
    private val now: Instant,
    private val riderZone: TimeZone,
) {
    private val configs: Map<SpotId, SpotConfig> = spots.associate { it.spot.spot.id to it.spot.toSpotConfig() }
    private val today: LocalDate = now.toLocalDateTime(riderZone).date

    fun map(days: List<DayEvaluationEntity>): List<DayUi> =
        days.map { day ->
            when (val decision = day.decision) {
                is DayDecision.Go -> decision.toUi(day)
                is DayDecision.NoGo -> decision.toUi()
            }
        }

    private fun DayDecision.Go.toUi(day: DayEvaluationEntity): DayUi.Go {
        val rides = ridesTo(window.spotId, date)
        val takingPart = rides.any { it.role != RideRoleEntity.NONE }
        return DayUi.Go(
            date = date,
            label = label(date),
            spotId = window.spotId,
            spotName = nameOf(window.spotId),
            window = window.ridingTime(),
            wind = windRange(day),
            kiteM2 = recommendedKiteM2,
            leaveBy = leaveBy.clockTime(riderZone),
            leaveNow = now >= leaveBy,
            confidence = confidence,
            rides = rides,
            hasCrew = crew.hasCrew,
            alternatives = alternatives.map { AlternativeUi(it.spotId, nameOf(it.spotId), it.ridingTime()) },
            offer = if (crew.hasCrew && !takingPart) RideOfferUi(window.spotId, window.usableStart, window.end, leaveBy) else null,
        )
    }

    private fun DayDecision.NoGo.toUi(): DayUi.NoGo =
        DayUi.NoGo(
            date = date,
            label = label(date),
            reason = primaryReason,
            spots = details.map { SpotReasonUi(it.spotId, nameOf(it.spotId), it.reason) },
            nextGoDay = nextGoDay?.let(::label),
        )

    /** The lowest and highest wind of the hours the rider can actually ride, in the rider's unit. */
    private fun windRange(day: DayEvaluationEntity): WindRange? {
        val low = day.windLow
        val high = day.windHigh
        val unit = profile.windUnit
        return if (low == null || high == null) null else WindRange(low.rounded(unit), high.rounded(unit), unit)
    }

    /** Scheduled rides of the rider's crews to [spotId] whose window starts on [date] at that spot. */
    private fun ridesTo(
        spotId: SpotId,
        date: LocalDate,
    ): List<RideUi> {
        val zone = zoneOf(spotId)
        return crew.rides
            .filter { it.spotId == spotId && it.status == RideStatus.SCHEDULED && it.windowStart.toLocalDateTime(zone).date == date }
            .sortedBy { it.departAt }
            .map { ride ->
                RideUi(
                    id = ride.id,
                    driverName = ride.driver.displayName,
                    departAt = ride.departAt.clockTime(riderZone),
                    departed = RideRulesMapper.hasDeparted(ride, now),
                    seatsFree = ride.seatsFree,
                    role = RideRulesMapper.role(ride, crew.userId),
                    pending = ride.pending,
                )
            }
    }

    /**
     * When the rider can be on the water: from [SessionWindow.usableStart], which is later than the window's start once
     * the time to leave has passed, until the end.
     */
    private fun SessionWindow.ridingTime(): TimeRange {
        val zone = zoneOf(spotId)
        return TimeRange(usableStart.clockTime(zone), end.clockTime(zone))
    }

    private fun label(date: LocalDate): DayLabel = dayLabelOf(date, today)

    private fun zoneOf(spotId: SpotId): TimeZone = configs.getValue(spotId).timeZone

    private fun nameOf(spotId: SpotId): String = configs.getValue(spotId).name
}
