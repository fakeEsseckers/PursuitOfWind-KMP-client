package io.github.esseckers.pursuitofwind.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.map.SpotMapUi
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/**
 * The map in tests, where MapLibre cannot run: an empty box tagged `map` that keeps what it was last asked to draw
 * ([shown]) and how much of it the screen covers ([covered]), and lets a test press, tap and pick spots as a rider
 * would, and say whether the base map loaded. Call those on the UI thread (`runOnIdle`).
 */
internal class ScriptedSpotMap : SpotMap {
    var shown: SpotMapUi? = null
        private set
    var covered: PaddingValues? = null
        private set
    private var onLongPress: (GeoPoint) -> Unit = {}
    private var onTap: (GeoPoint) -> Unit = {}
    private var onSpot: (SpotId) -> Unit = {}
    private var onBaseMap: (Boolean) -> Unit = {}

    @Composable
    override fun Show(
        map: SpotMapUi,
        covered: PaddingValues,
        onLongPress: (GeoPoint) -> Unit,
        onTap: (GeoPoint) -> Unit,
        onSpot: (SpotId) -> Unit,
        onBaseMap: (loaded: Boolean) -> Unit,
        modifier: Modifier,
    ) {
        SideEffect {
            shown = map
            this.covered = covered
            this.onLongPress = onLongPress
            this.onTap = onTap
            this.onSpot = onSpot
            this.onBaseMap = onBaseMap
        }
        Box(modifier = modifier.fillMaxSize().testTag("map"))
    }

    fun longPress(point: GeoPoint) = onLongPress(point)

    fun tap(point: GeoPoint) = onTap(point)

    fun tapSpot(spotId: SpotId) = onSpot(spotId)

    fun baseMap(loaded: Boolean) = onBaseMap(loaded)
}
