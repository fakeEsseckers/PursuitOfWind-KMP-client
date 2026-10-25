package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model

/** What the rider can do in the ride offer form. */
internal sealed interface RideOfferIntent {
    data class ChooseCrew(
        val crewId: String,
    ) : RideOfferIntent

    data object FewerSeats : RideOfferIntent

    data object MoreSeats : RideOfferIntent

    data class ChangeMeetingPoint(
        val text: String,
    ) : RideOfferIntent

    /** Offer the ride to the chosen crew. */
    data object Submit : RideOfferIntent
}

/** One-off events of the ride offer form. */
internal sealed interface RideOfferEffect {
    /** The ride is offered; the form closes. */
    data object Offered : RideOfferEffect
}
