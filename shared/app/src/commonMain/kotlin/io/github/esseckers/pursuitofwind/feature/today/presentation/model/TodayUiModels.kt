package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.core.domain.entity.RideRoleEntity
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// UI models of the Today screen: formatted for display, with no domain logic left to run in composables.

/** The answer for one day, formatted for display: times are local to the spot, wind in the rider's unit. */
internal sealed interface DayUi {
    val date: LocalDate
    val label: DayLabel

    data class Go(
        override val date: LocalDate,
        override val label: DayLabel,
        val spotId: SpotId,
        val spotName: String,
        /** When the rider can ride: the window, starting later if the time to leave has passed. */
        val window: TimeRange,
        val wind: WindRange?,
        val kiteM2: Double?,
        val leaveBy: String,
        val leaveNow: Boolean,
        val confidence: Confidence,
        val rides: List<RideUi>,
        val hasCrew: Boolean,
        val alternatives: List<AlternativeUi>,
        /** What "I'm going" offers the crew; null without a crew, or when the rider already drives or rides along. */
        val offer: RideOfferUi? = null,
    ) : DayUi

    /** [spots] holds the reason of every favorite spot; with one spot that is the main [reason] again. */
    data class NoGo(
        override val date: LocalDate,
        override val label: DayLabel,
        val reason: Reason,
        val spots: List<SpotReasonUi>,
        val nextGoDay: DayLabel?,
    ) : DayUi
}

/** Mean wind across a window, rounded, in [unit]. */
internal data class WindRange(
    val low: Int,
    val high: Int,
    val unit: WindUnit,
)

internal data class RideUi(
    val id: String,
    val driverName: String,
    val departAt: String,
    val departed: Boolean,
    val seatsFree: Int,
    val role: RideRoleEntity,
    val pending: Boolean,
)

/**
 * A ride the rider can offer for a GO window at [spotId]: the riding time from [windowStart] to [windowEnd], and
 * [departAt], the time to leave. The ride offer form decides when the car actually leaves.
 */
internal data class RideOfferUi(
    val spotId: SpotId,
    val windowStart: Instant,
    val windowEnd: Instant,
    val departAt: Instant,
)

internal data class AlternativeUi(
    val spotId: SpotId,
    val spotName: String,
    val window: TimeRange,
)

internal data class SpotReasonUi(
    val spotId: SpotId,
    val spotName: String,
    val reason: Reason,
)
