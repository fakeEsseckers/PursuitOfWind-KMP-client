package io.github.esseckers.pursuitofwind.feature.today.presentation.preview

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.AlternativeUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.DayUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideOfferUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RideUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.SpotReasonUi
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.WindRange
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * States of the Today screen for previews and UI tests, modelled on the sample rider (Alex, 80 kg, kites 7 / 9 / 12 m²)
 * on a Thursday: gusty today, a short window tomorrow, Saturday on at Nordstrand Bay with Andrei driving.
 */
internal object TodayPreviewData {
    private val saturday = DayLabel.Weekday(DayOfWeek.SATURDAY)

    val gustyToday =
        DayUi.NoGo(
            date = LocalDate(2026, 10, 22),
            label = DayLabel.Today,
            reason = Reason.GUSTY,
            spots =
                listOf(
                    SpotReasonUi(SpotId("lake-weiss"), "Lake Weiss", Reason.GUSTY),
                    SpotReasonUi(SpotId("nordstrand-bay"), "Nordstrand Bay", Reason.OFFSHORE_DANGER),
                ),
            nextGoDay = saturday,
        )

    val tooShortTomorrow =
        DayUi.NoGo(
            date = LocalDate(2026, 10, 23),
            label = DayLabel.Tomorrow,
            reason = Reason.WINDOW_TOO_SHORT,
            spots = listOf(SpotReasonUi(SpotId("nordstrand-bay"), "Nordstrand Bay", Reason.WINDOW_TOO_SHORT)),
            nextGoDay = saturday,
        )

    val saturdayOn =
        DayUi.Go(
            date = LocalDate(2026, 10, 24),
            label = saturday,
            spotId = SpotId("nordstrand-bay"),
            spotName = "Nordstrand Bay",
            window = TimeRange("13:00", "17:00"),
            wind = WindRange(18, 22, WindUnit.KNOTS),
            kiteM2 = 9.0,
            leaveBy = "10:30",
            leaveNow = false,
            confidence = Confidence.HIGH,
            rides =
                listOf(
                    RideUi(
                        "rid_7f3c",
                        "Andrei",
                        "08:30",
                        departed = false,
                        seatsFree = 2,
                        youDrive = false,
                        youRide = false,
                        pending = false,
                    ),
                ),
            hasCrew = true,
            alternatives = listOf(AlternativeUi(SpotId("lake-weiss"), "Lake Weiss", TimeRange("14:00", "17:00"))),
            offer =
                RideOfferUi(
                    spotId = SpotId("nordstrand-bay"),
                    windowStart = Instant.parse("2026-10-24T11:00:00Z"),
                    windowEnd = Instant.parse("2026-10-24T15:00:00Z"),
                    departAt = Instant.parse("2026-10-24T08:30:00Z"),
                ),
        )

    val modelsDisagree =
        saturdayOn.copy(
            spotId = SpotId("salt-lagoon"),
            spotName = "Salt Lagoon",
            wind = WindRange(14, 18, WindUnit.KNOTS),
            kiteM2 = 12.0,
            confidence = Confidence.LOW,
            rides = emptyList(),
            alternatives = emptyList(),
            offer = null,
        )

    val ready = TodayState(Content.Ready(listOf(gustyToday, tooShortTomorrow, saturdayOn)), updatedAgo = 12.minutes)
    val offline = ready.copy(updatedAgo = 5.hours, notice = TodayState.Notice.OFFLINE)
    val loading = TodayState(isRefreshing = true)
    val noProfile = TodayState(Content.Empty(TodayState.Missing.PROFILE))
    val noSpots = TodayState(Content.Empty(TodayState.Missing.FAVORITE_SPOTS))
    val error = TodayState(Content.Error(Res.string.error_network))
}
