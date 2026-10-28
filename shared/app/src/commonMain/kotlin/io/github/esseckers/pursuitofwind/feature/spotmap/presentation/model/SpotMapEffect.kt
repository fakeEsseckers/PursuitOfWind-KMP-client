package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model

/** One-off events of the map of spots, handled by the app outside the screen. */
internal sealed interface SpotMapEffect {
    /** A spot was added, followed or changed: the map closes. */
    data object Finished : SpotMapEffect
}
