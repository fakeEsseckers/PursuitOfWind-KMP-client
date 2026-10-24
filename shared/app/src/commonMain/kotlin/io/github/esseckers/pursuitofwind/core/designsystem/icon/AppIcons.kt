package io.github.esseckers.pursuitofwind.core.designsystem.icon

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
    val Group: ImageVector =
        icon(
            "Group",
            "M12.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0",
            "M2.5 20c0-3.2 2.9-5.5 6.5-5.5s6.5 2.3 6.5 5.5",
            "M15.5 4.6a3.5 3.5 0 0 1 0 6.8",
            "M17.5 14.7c2.4 0.6 4 2.6 4 5.3",
        )
    val Pin: ImageVector =
        icon(
            "Pin",
            "M12 21s7-6.5 7-11.5a7 7 0 0 0-14 0C5 14.5 12 21 12 21Z",
            "M14.5 9.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
        )

    // Back and forward point the other way in right-to-left languages.
    val Back: ImageVector = icon("Back", "M20 12H4", "M10 6l-6 6l6 6", autoMirror = true)
    val Forward: ImageVector = icon("Forward", "M9.5 5.5l6.5 6.5l-6.5 6.5", autoMirror = true)
    val Refresh: ImageVector = icon("Refresh", "M20 12a8 8 0 1 1-2.35-5.65", "M20 4.5v5h-5")
    val Plus: ImageVector = icon("Plus", "M12 5v14", "M5 12h14")
    val Minus: ImageVector = icon("Minus", "M5 12h14")
    val Check: ImageVector = icon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val ExpandMore: ImageVector = icon("ExpandMore", "M6 9.5l6 6l6-6")
    val ExpandLess: ImageVector = icon("ExpandLess", "M6 14.5l6-6l6 6")

    private fun icon(
        name: String,
        vararg paths: String,
        autoMirror: Boolean = false,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = autoMirror,
            ).apply {
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
