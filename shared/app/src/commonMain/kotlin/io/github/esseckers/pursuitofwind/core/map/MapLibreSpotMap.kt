package io.github.esseckers.pursuitofwind.core.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.domain.mapper.GeoMapper
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.spot_map_water_label
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.overlay.include
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/**
 * [SpotMap] drawn by MapLibre with OpenFreeMap's vector tiles (OpenStreetMap data, free and without a key; MapLibre
 * shows the attribution). Bound on Android and iOS. The JVM runs tests only, and there MapLibre needs Java 25 and a
 * GPU, so tests bind a map of their own.
 */
internal class MapLibreSpotMap : SpotMap {
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
        val currentMap by rememberUpdatedState(map)
        val currentOnSpot by rememberUpdatedState(onSpot)
        val reportBaseMap by rememberUpdatedState(onBaseMap)
        val colors = MapColors.current()
        val interactions = rememberInteractions(onLongPress, onTap)
        // Another attempt starts the map over, which loads the base map again.
        key(map.attempt) {
            val state =
                rememberMapState(
                    baseStyle = BaseStyle.Uri(STYLE_URL),
                    initialCameraPosition = CameraPosition(center = map.start.center.toPosition(), zoom = map.start.zoom),
                ) {
                    SpotLayers(currentMap, colors, currentOnSpot)
                }
            val loadState = state.style.loadState
            LaunchedEffect(loadState) {
                when (loadState) {
                    StyleLoadState.Ready -> reportBaseMap(true)
                    is StyleLoadState.Failed -> reportBaseMap(false)
                    else -> Unit
                }
            }
            // The credits OpenStreetMap's licence asks for, and MapLibre's controls, stay above what the screen draws over.
            MaplibreMap(
                modifier = modifier,
                state = state,
                viewportInsets = covered,
                interactions = interactions,
                overlay = {
                    include(MapOverlay.Full)
                    Shore(currentMap, state, colors)
                },
            )
        }
    }

    private companion object {
        /** OpenFreeMap's general-purpose style: roads, water and place names. */
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    }
}

/** Long presses and taps on the map; a tap on a marker is the marker's, unless the marker lets it through. */
@Composable
private fun rememberInteractions(
    onLongPress: (GeoPoint) -> Unit,
    onTap: (GeoPoint) -> Unit,
): MapInteractions {
    val longPress by rememberUpdatedState(onLongPress)
    val tap by rememberUpdatedState(onTap)
    return remember {
        MapInteractions {
            callbacks {
                longClick {
                    onEvent { event ->
                        event.position?.let { longPress(it.toGeoPoint()) }
                        ClickResult.Consume
                    }
                }
                // After the markers: a tap on one is theirs, a tap anywhere else is the map's.
                click {
                    onUnhandled { event ->
                        event.position?.let { tap(it.toGeoPoint()) }
                        ClickResult.Consume
                    }
                }
            }
        }
    }
}

/** The shore of the spot being placed, around its pin, once the rider showed where its water is. */
@Composable
private fun MapOverlayScope.Shore(
    map: SpotMapUi,
    state: MapState,
    colors: MapColors,
) {
    val pin = map.pin
    val water = map.water
    val sectors = map.sectors
    if (pin != null && water != null && sectors != null) {
        ShoreCompass(
            water = water,
            sectors = sectors,
            // The rendered camera follows a rotation as it happens; the camera position only once it stops.
            mapBearing = state.viewport?.cameraPosition?.bearing ?: state.cameraPosition.bearing,
            waterLabel = stringResource(Res.string.spot_map_water_label),
            rideable = colors.rideable,
            danger = colors.danger,
            arrow = colors.pin,
            modifier = Modifier.placedAt(pin.toPosition()),
        )
    }
}

/**
 * The colors the map draws with, from the app's theme. The base map is light in both themes, so names are dark with a
 * light halo, as on any street map.
 */
private data class MapColors(
    val rideable: Color,
    val danger: Color,
    val pin: Color,
    val followed: Color,
    val other: Color,
    val outline: Color,
    val name: Color,
) {
    companion object {
        @Composable
        fun current(): MapColors {
            val verdicts = PursuitOfWindTheme.colors
            val scheme = MaterialTheme.colorScheme
            return MapColors(
                rideable = verdicts.go.accent,
                danger = verdicts.noGo.accent,
                pin = scheme.tertiary,
                followed = scheme.primary,
                other = scheme.secondary,
                outline = Color.White,
                name = Color.Black,
            )
        }
    }
}

/** The pin of the spot being placed under the catalog's markers, so a marker on top of the pin stays tappable. */
@Composable
@MaplibreComposable
private fun SpotLayers(
    map: SpotMapUi,
    colors: MapColors,
    onSpot: (SpotId) -> Unit,
) {
    val pin = map.pin
    if (pin != null) {
        val point = rememberGeoJsonSource(points(listOf(pin to JsonObject(emptyMap()))))
        CircleLayer(
            id = "spot-pin",
            source = point,
            color = const(colors.pin),
            radius = const(9.dp),
            strokeColor = const(colors.outline),
            strokeWidth = const(2.dp),
        )
    }
    // While a spot is being placed, a tap on a marker is a tap on the water there.
    Markers(map.markers, colors, onSpot = if (pin == null) onSpot else null)
}

/**
 * The catalog's spots: the rider's favorites stand out, the selected one most, each with its name under it. A tap on
 * one goes to [onSpot]; with none, it goes on to the map.
 */
@Composable
@MaplibreComposable
private fun Markers(
    markers: List<SpotMarkerUi>,
    colors: MapColors,
    onSpot: ((SpotId) -> Unit)?,
) {
    val source = rememberGeoJsonSource(markers.markerPoints())
    val followed = rememberGeoJsonSource(markers.filter { it.followed }.markerPoints())
    val selected = rememberGeoJsonSource(markers.filter { it.selected }.markerPoints())
    val tapped = markerTaps(onSpot)
    CircleLayer(
        id = "spots",
        source = source,
        color = const(colors.other),
        radius = const(7.dp),
        strokeColor = const(colors.outline),
        strokeWidth = const(2.dp),
        onClick = tapped,
        hitPadding = MARKER_HIT_PADDING,
    )
    CircleLayer(
        id = "spots-mine",
        source = followed,
        color = const(colors.followed),
        radius = const(8.dp),
        strokeColor = const(colors.outline),
        strokeWidth = const(2.dp),
        onClick = tapped,
        hitPadding = MARKER_HIT_PADDING,
    )
    CircleLayer(
        id = "spot-selected",
        source = selected,
        color = const(colors.pin),
        radius = const(10.dp),
        strokeColor = const(colors.outline),
        strokeWidth = const(3.dp),
        onClick = tapped,
        hitPadding = MARKER_HIT_PADDING,
    )
    SymbolLayer(
        id = "spot-names",
        source = source,
        textField = format(span(feature[NAME].asString())),
        textFont = const(listOf(FONT)),
        textSize = const(0.9.em),
        textOffset = textOffset(0.em, 1.4.em),
        textColor = const(colors.name),
        textHaloColor = const(colors.outline),
        textHaloWidth = const(1.5.dp),
    )
}

/** A tap on a marker goes to [onSpot]; with none, it goes on to the map. */
private fun markerTaps(onSpot: ((SpotId) -> Unit)?): FeaturesClickHandler =
    { features ->
        val spotId =
            features.firstNotNullOfOrNull {
                it.properties
                    ?.get(ID)
                    ?.jsonPrimitive
                    ?.content
            }
        if (onSpot == null || spotId == null) {
            ClickResult.Pass
        } else {
            onSpot(SpotId(spotId))
            ClickResult.Consume
        }
    }

private fun List<SpotMarkerUi>.markerPoints(): GeoJsonData = points(map { it.location to it.properties() })

private fun SpotMarkerUi.properties(): JsonObject = JsonObject(mapOf(ID to JsonPrimitive(id.value), NAME to JsonPrimitive(name)))

private fun points(points: List<Pair<GeoPoint, JsonObject>>): GeoJsonData =
    GeoJsonData.Features(FeatureCollection(points.map { (point, properties) -> Feature(Point(point.toPosition()), properties) }))

private fun GeoPoint.toPosition(): Position = Position(longitude = longitude, latitude = latitude)

/** A press on another copy of the world, past the date line, is on this one. */
private fun Position.toGeoPoint(): GeoPoint = GeoPoint(latitude = latitude, longitude = GeoMapper.normalizeLongitude(longitude))

private const val ID = "id"
private const val NAME = "name"

/** A font OpenFreeMap serves glyphs for; MapLibre's default fonts are not among them. */
private const val FONT = "Noto Sans Regular"

/** Around a 7 dp marker, a touch target of 48 dp. */
private val MARKER_HIT_PADDING = 17.dp
