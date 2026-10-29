package io.github.esseckers.pursuitofwind.core.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/**
 * A map of spots, drawn from [SpotMapUi]. Android and iOS draw it with MapLibre ([MapLibreSpotMap]); tests bind one a
 * test can drive, since the map needs a GPU and its tiles come from the network. Each platform binds its own in Koin.
 */
internal interface SpotMap {
    /**
     * Shows [map]. [onLongPress] and [onTap] get the place the rider pressed; a tap on a spot's marker goes to
     * [onSpot] instead, unless a spot is being placed ([SpotMapUi.pin]): then every tap is a tap on the map. [covered]
     * is the part of the map the screen draws over, kept clear for the map's own credits and controls. [onBaseMap] says
     * whether the base map (streets, water, names) loaded; without a connection it does not, and nothing shows. The map
     * opens at [SpotMapUi.start], fitting [MapCameraUi.fit] once it knows its size, and then belongs to the rider: later
     * states do not move it, but a new [SpotMapUi.attempt] starts it over.
     */
    @Composable
    fun Show(
        map: SpotMapUi,
        covered: PaddingValues,
        onLongPress: (GeoPoint) -> Unit,
        onTap: (GeoPoint) -> Unit,
        onSpot: (SpotId) -> Unit,
        onBaseMap: (loaded: Boolean) -> Unit,
        modifier: Modifier,
    )
}
