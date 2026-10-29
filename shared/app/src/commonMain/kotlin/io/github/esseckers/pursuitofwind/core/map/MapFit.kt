package io.github.esseckers.pursuitofwind.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.map.MapState
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.Position

/**
 * Brings all of [places] into sight once the map knows its size: clear of what the screen draws over it, with room for
 * the spots' names, and no closer than a region. Once per map, so after that, and after the app's state is restored,
 * the camera is the rider's.
 */
@Composable
internal fun FitOnce(
    state: MapState,
    places: List<GeoPoint>,
) {
    var fitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (fitted || places.isEmpty()) return@LaunchedEffect
        state.awaitViewport()
        val camera =
            state.cameraForCoordinates(
                places.map {
                    Position(longitude = it.longitude, latitude = it.latitude)
                },
                CameraFit(fitPadding = FIT_PADDING),
            )
        state.setCameraPosition(camera.copy(zoom = minOf(camera.zoom, MapCameraUi.REGION_ZOOM)))
        fitted = true
    }
}

/** Around the outermost spots when the map fits them: half a long name to each side, and the name under each. */
private val FIT_PADDING = DpPadding(left = 56.dp, top = 24.dp, right = 56.dp, bottom = 48.dp)
