package io.github.esseckers.pursuitofwind.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own line icons on a 24-unit grid, drawn with round 2-unit strokes. They are tinted like any icon
 * (`Icon(imageVector, …, tint = …)`), so the stroke color here does not matter.
 */
internal object AppIcons {
    private const val CIRCLE = "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0"

    val Go: ImageVector = icon("Go", CIRCLE, "M7.5 12.5l3 3l6-6.5")
    val Marginal: ImageVector = icon("Marginal", "M12 3.5L21.5 20H2.5Z", "M12 10v4", "M12 17v0.01")
    val NoGo: ImageVector = icon("NoGo", CIRCLE, "M9 9l6 6", "M15 9l-6 6")
    val Car: ImageVector =
        icon(
            "Car",
            "M5 16H3.5v-3.5L5.5 7.5h13l2 5V16H19",
            "M3.5 12.5h17",
            "M9 16h6",
            "M9 16.5a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
            "M19 16.5a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
        )
    val Clock: ImageVector = icon("Clock", CIRCLE, "M12 7v5l3 2")
    val Wind: ImageVector = icon("Wind", "M3 8h10a3 3 0 1 0-3-3", "M3 16h13a3 3 0 1 1-3 3", "M3 12h16")
    val Kite: ImageVector = icon("Kite", "M12 2.5l6 6.5l-6 6.5l-6-6.5Z", "M6 9h12", "M12 15.5c-1.5 2 1.5 3.5 0 6")
    val CloudOff: ImageVector =
        icon(
            "CloudOff",
            "M8.5 7.4A5.5 5.5 0 0 1 17 10h0.5a4 4 0 0 1 2.4 7.2",
            "M17 18H7a3.5 3.5 0 0 1-0.6-7",
            "M3 3l18 18",
        )
    val Info: ImageVector = icon("Info", CIRCLE, "M12 11v5", "M12 8v0.01")
    val Person: ImageVector = icon("Person", "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M4 20c0-3.5 3.5-6 8-6s8 2.5 8 6")
    val Pin: ImageVector =
        icon(
            "Pin",
            "M12 21s7-6.5 7-11.5a7 7 0 0 0-14 0C5 14.5 12 21 12 21Z",
            "M14.5 9.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
        )

    private fun icon(
        name: String,
        vararg paths: String,
    ): ImageVector =
        ImageVector
            .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { path ->
                    addPath(
                        pathData = addPathNodes(path),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()
}
