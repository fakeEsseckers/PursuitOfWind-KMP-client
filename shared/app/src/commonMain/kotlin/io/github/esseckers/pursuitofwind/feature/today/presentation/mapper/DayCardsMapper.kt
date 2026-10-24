package io.github.esseckers.pursuitofwind.feature.today.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.nextQuarterHour
import io.github.esseckers.pursuitofwind.core.ui.rounded
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.model.CrewRides
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

/** Today and the next two days. */
internal const val DAYS_SHOWN = 3

/** A favorite spot with its cached forecast hours. */
internal data class SpotWithForecast(
    val spot: MySpot,
    val hours: List<ForecastHour>,
)

/**
 * Runs the decision engine on the cached data and formats the first [DAYS_SHOWN] days for display. Session windows are
 * shown in the spot's local time, because that is where they happen; departures and the "today" label use [riderZone],
 * where the rider is.
 */
internal class DayCardsMapper(
    private val profile: Profile,
    spots: List<SpotWithForecast>,
    private val crew: CrewRides,
    private val now: Instant,
    private val riderZone: TimeZone,
) {
    private val configs: Map<SpotId, SpotConfig> = spots.associate { it.spot.spot.id to it.spot.toSpotConfig() }
    private val hours: Map<SpotId, List<ForecastHour>> = spots.associate { it.spot.spot.id to it.hours }
    private val today: LocalDate = now.toLocalDateTime(riderZone).date

    fun map(): List<DayUi> {
        val forecasts = configs.map { (id, config) -> SpotForecast(config, hours.getValue(id)) }
        return DecisionEngine
            .decideDays(forecasts, profile.rider, now)
            // A spot behind the rider's time zone can still be on the rider's yesterday.
            .filter { it.date >= today }
            .take(DAYS_SHOWN)
            .map { decision ->
                when (decision) {
                    is DayDecision.Go -> decision.toUi()
                    is DayDecision.NoGo -> decision.toUi()
                }
            }
    }

    private fun DayDecision.Go.toUi(): DayUi.Go {
        val rides = ridesTo(window.spotId, date)
        val busy = rides.any { it.youDrive || it.youRide }
        return DayUi.Go(
            date = date,
            label = label(date),
            spotId = window.spotId,
            spotName = nameOf(window.spotId),
            window = window.ridingTime(),
            wind = windRange(window),
            kiteM2 = recommendedKiteM2,
            leaveBy = leaveBy.clockTime(riderZone),
            leaveNow = now >= leaveBy,
            confidence = confidence,
            rides = rides,
            hasCrew = crew.hasCrew,
            alternatives = alternatives.map { AlternativeUi(it.spotId, nameOf(it.spotId), it.ridingTime()) },
            offer = if (crew.hasCrew && !busy) RideOfferUi(window.spotId, window.usableStart, window.end, departure(leaveBy)) else null,
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

    /**
     * When an offered ride leaves: at [leaveBy], or once that has passed, at the next quarter hour. The riding time
     * already starts from leaving now plus the drive and rigging, so the window still begins after it.
     */
    private fun departure(leaveBy: Instant): Instant = if (leaveBy > now) leaveBy else nextQuarterHour(now)

    /** The lowest and highest consensus mean wind of the hours the rider can actually ride, in the rider's unit. */
    private fun windRange(window: SessionWindow): WindRange? {
        val config = configs.getValue(window.spotId)
        val speeds =
            hours
                .getValue(window.spotId)
                .filter { it.start < window.end && it.start + 1.hours > window.usableStart }
                .map {
                    DecisionEngine
                        .evaluateHour(it, profile.rider, config)
                        .wind.mean
                        .rounded(profile.windUnit)
                }
        return if (speeds.isEmpty()) null else WindRange(speeds.min(), speeds.max(), profile.windUnit)
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
                    departed = ride.departAt <= now,
                    seatsFree = ride.seatsFree,
                    youDrive = ride.driver.userId == crew.userId,
                    youRide = ride.passengers.any { it.userId == crew.userId },
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
