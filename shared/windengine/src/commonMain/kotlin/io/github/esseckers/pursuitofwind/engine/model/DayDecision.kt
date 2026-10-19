package io.github.esseckers.pursuitofwind.engine.model

import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

/** Forecast hours for one spot. Order does not matter; each hour start may appear once. */
public data class SpotForecast(
    public val spot: SpotConfig,
    public val hours: List<ForecastHour>,
) {
    init {
        require(hours.map { it.start }.toSet().size == hours.size) { "Forecast for spot ${spot.id} repeats an hour" }
    }
}

/** Answer to "should I go riding on [date]?" across the rider's favorite spots. [date] is local to the spots. */
public sealed interface DayDecision {
    public val date: LocalDate

    /** Go: [window] is the best qualifying window of the day; [alternatives] are the next two at most. */
    public data class Go(
        override val date: LocalDate,
        public val window: SessionWindow,
        public val alternatives: List<SessionWindow>,
    ) : DayDecision {
        public val spotId: SpotId get() = window.spotId
        public val leaveBy: Instant get() = window.leaveBy
        public val confidence: Confidence get() = window.confidence
        public val recommendedKiteM2: Double? get() = window.recommendedKiteM2
        public val usable: Duration get() = window.usable
    }

    /**
     * No-go, with the main reason across all favorite spots and one reason per spot.
     *
     * @property nextGoDay the earliest later date in the forecast with a [Go], if any.
     */
    public data class NoGo(
        override val date: LocalDate,
        public val primaryReason: Reason,
        public val details: List<SpotNoGo>,
        public val nextGoDay: LocalDate?,
    ) : DayDecision
}

/** Why one spot does not work on a no-go day. */
public data class SpotNoGo(
    public val spotId: SpotId,
    public val reason: Reason,
)
