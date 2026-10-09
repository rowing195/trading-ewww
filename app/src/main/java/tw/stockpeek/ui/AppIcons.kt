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
