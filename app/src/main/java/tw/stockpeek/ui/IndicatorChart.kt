package tw.stockpeek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import tw.stockpeek.data.Candle
import tw.stockpeek.data.SubIndicator
import tw.stockpeek.data.kd
import tw.stockpeek.data.macd
import tw.stockpeek.data.rsi
import tw.stockpeek.ui.theme.LocalMarketColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 副圖資料：指標線、MACD 柱狀體、參考線（KD／RSI 的 80、20，MACD 的零軸），
 * fixedRange 為 null 時依畫面內的數值上下對稱縮放。
 */
class SubSeries(
    val lines: List<SeriesLine>,
    val histogram: DoubleArray? = null,
    val refLines: List<Double> = emptyList(),
    val fixedRange: ClosedFloatingPointRange<Double>? = null,
    val decimals: Int = 1,
)

fun subSeriesOf(bars: List<Candle>, indicator: SubIndicator, first: Color, second: Color): SubSeries {
    val closes = bars.map { it.close }
    return when (indicator) {
        SubIndicator.KD -> kd(bars).let {
            SubSeries(
                lines = listOf(SeriesLine("K", it.k, first), SeriesLine("D", it.d, second)),
                refLines = listOf(80.0, 20.0),
                fixedRange = 0.0..100.0,
            )
        }
        SubIndicator.MACD -> macd(closes).let {
            SubSeries(
                lines = listOf(SeriesLine("DIF", it.dif, first), SeriesLine("MACD", it.signal, second)),
                histogram = it.osc,
                refLines = listOf(0.0),
                decimals = 2,
            )
        }
        SubIndicator.RSI -> SubSeries(
            lines = listOf(SeriesLine("RSI6", rsi(closes, 6), first), SeriesLine("RSI12", rsi(closes, 12), second)),
            refLines = listOf(80.0, 20.0),
            fixedRange = 0.0..100.0,
        )
    }
}

/** 副圖：跟主圖共用 viewport，平移縮放與十字線都同步。 */
@Composable
fun IndicatorChart(
    bars: List<Candle>,
    series: SubSeries,
    viewport: ChartViewport,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val axisStyle = MaterialTheme.typography.labelSmall.tabular().copy(color = scheme.onSurfaceVariant)
    val barCount by rememberUpdatedState(bars.size)
    val selected by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxSize()
            .chartGestures(viewport, { barCount }, { selected }) { index, _ -> select(index) },
    ) {
        val n = bars.size
        if (n == 0) return@Canvas
        val axisW = CHART_AXIS_WIDTH.toPx()
        val plotRight = size.width - axisW
        val window = ChartWindow(n, viewport, plotRight)
        if (window.last < window.first) return@Canvas

        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val range = series.fixedRange ?: run {
            var amp = 0.0
            for (i in window.first..window.last) {
                series.lines.forEach { line -> line.values[i].let { if (!it.isNaN()) amp = max(amp, abs(it)) } }
                series.histogram?.let { amp = max(amp, abs(it[i])) }
            }
            if (amp == 0.0) amp = 1.0
            -amp * 1.1..amp * 1.1
        }
        fun yOf(v: Double): Float =
            (top + (range.endInclusive - v) / (range.endInclusive - range.start) * (bottom - top)).toFloat()

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        series.refLines.forEach { v ->
            val y = yOf(v)
            drawLine(scheme.outlineVariant, Offset(0f, y), Offset(plotRight, y), strokeWidth = 1f, pathEffect = dash)
            val layout = measurer.measure(formatPrice(v), axisStyle)
            val ty = (y - layout.size.height / 2f).coerceIn(0f, max(0f, size.height - layout.size.height))
            drawText(layout, topLeft = Offset(plotRight + 4.dp.toPx(), ty))
        }

        val from = max(0, window.first - 1)
        val to = min(n - 1, window.last + 1)
        clipRect(0f, 0f, plotRight, size.height) {
            series.histogram?.let { osc ->
                val y0 = yOf(0.0)
                val bodyW = max(1f, window.barWidth * 0.68f)
                for (i in window.first..window.last) {
                    val y = yOf(osc[i])
                    val color = if (osc[i] >= 0) market.up else market.down
                    drawRect(
                        color.copy(alpha = 0.75f),
                        Offset(window.xOf(i) - bodyW / 2, min(y, y0)),
                        Size(bodyW, max(abs(y - y0), 1f)),
                    )
                }
            }
            series.lines.forEach { line ->
                drawPath(
                    linePath(line.values, from, to, window::xOf, ::yOf),
                    line.color,
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }
        }

        val sel = selectedIndex
        if (sel != null && sel in 0 until n) {
            val x = window.xOf(sel)
            if (x in 0f..plotRight) {
                drawLine(scheme.onSurface.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
        }
    }
}
