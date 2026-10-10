package tw.stockpeek.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** material-icons-core 沒有的幾個線條圖示。 */
object AppIcons {
    val Sort: ImageVector by lazy { strokeIcon("Sort", "M7 4v16M4 17l3 3 3-3M17 20V4M14 7l3-3 3 3") }

    val Tune: ImageVector by lazy {
        strokeIcon(
            "Tune",
            "M4 7h10M18 7h2M4 17h4M12 17h8M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val ArrowOutward: ImageVector by lazy { strokeIcon("ArrowOutward", "M7 17L17 7M8 7h9v9") }

    val Key: ImageVector by lazy {
        strokeIcon("Key", "M4 15a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M11 12l9-9M17 6l3 3M15 8l2 2")
    }

    val Eye: ImageVector by lazy {
        strokeIcon("Eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    }

    val EyeOff: ImageVector by lazy {
        strokeIcon(
            "EyeOff",
            "M3 3l18 18M10.6 5.1A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.2" +
                "M6.6 6.6C3.8 8.4 2 12 2 12s3.5 7 10 7c1.7 0 3.2-.4 4.5-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2",
        )
    }

    val Trash: ImageVector by lazy { strokeIcon("Trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3") }

    val Check: ImageVector by lazy { strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5") }

    val Alert: ImageVector by lazy { strokeIcon("Alert", "M12 4l9 16H3zM12 10v4M12 17h.01") }

    val Info: ImageVector by lazy { strokeIcon("Info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M12 11v5M12 8h.01") }

    val LineChart: ImageVector by lazy { strokeIcon("LineChart", "M3 17c3-6 6-8 9-5s6 1 9-5") }

    val SubChart: ImageVector by lazy { strokeIcon("SubChart", "M4 7h16M4 12h10M4 17h13") }

    val Bars: ImageVector by lazy { strokeIcon("Bars", "M5 20V12M10 20V8M15 20v-6M20 20V5") }

    private fun strokeIcon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
