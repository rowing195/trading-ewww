package tw.stockpeek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import tw.stockpeek.data.Bollinger
import tw.stockpeek.data.Candle
import tw.stockpeek.data.Timeframe
import tw.stockpeek.ui.theme.LocalMarketColors
import tw.stockpeek.ui.theme.onColorFor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_VISIBLE = 12f
private const val MAX_VISIBLE = 300f

/** 均線離畫面內 K 棒的範圍不超過這個比例才納入價格範圍；再遠就讓它出圖，改在圖邊標價位。 */
private const val MA_RANGE_SLACK = 0.3

/** 右側價格刻度的寬度；主圖、副圖一樣寬，K 棒才會上下對齊。 */
val CHART_AXIS_WIDTH = 58.dp

/** 可視範圍：畫面上放幾根 K 棒，以及最右邊藏了幾根（0 = 貼齊最新一根）。 */
@Stable
class ChartViewport(initialVisible: Float) {
    var visible by mutableFloatStateOf(initialVisible)
    var rightOffset by mutableFloatStateOf(0f)

    fun resolvedVisible(n: Int): Float =
        visible.coerceIn(MIN_VISIBLE, max(MIN_VISIBLE, min(n.toFloat(), MAX_VISIBLE)))

    fun resolvedOffset(n: Int): Float =
        rightOffset.coerceIn(0f, max(0f, n - resolvedVisible(n)))

    fun clamp(n: Int) {
        visible = resolvedVisible(n)
        rightOffset = resolvedOffset(n)
    }
}

/** 各週期一打開時畫面上放幾根 K 棒。 */
fun initialVisibleBars(tf: Timeframe): Float = when (tf) {
    Timeframe.MIN5 -> 54f   // 一天 54 根 5 分 K
    Timeframe.DAY -> 60f
    Timeframe.WEEK -> 52f
    Timeframe.MONTH -> 48f
}

/** 依可視範圍算出畫面上的第一根、最後一根與每根寬度；主圖、副圖與手勢共用。 */
class ChartWindow(n: Int, viewport: ChartViewport, val plotRight: Float) {
    private val endF = n - viewport.resolvedOffset(n)
    val barWidth = plotRight / viewport.resolvedVisible(n)
    val first = max(0, floor(endF - viewport.resolvedVisible(n)).toInt())
    val last = min(n - 1, ceil(endF).toInt() - 1)

    fun xOf(i: Int): Float = plotRight - (endF - i - 0.5f) * barWidth

    fun indexAt(x: Float): Int =
        (endF - 0.5f - (plotRight - x) / barWidth).roundToInt().coerceIn(first, max(first, last))
}

/** 一條折線：均線或副圖的指標線。 */
class SeriesLine(val label: String, val values: DoubleArray, val color: Color)

/** 把 values[from..to] 連成折線，遇到 NaN 就斷開。 */
fun linePath(values: DoubleArray, from: Int, to: Int, xOf: (Int) -> Float, yOf: (Double) -> Float): Path {
    val path = Path()
    var started = false
    for (i in max(0, from)..min(values.lastIndex, to)) {
        val v = values[i]
        if (v.isNaN()) {
            started = false
            continue
        }
        if (started) path.lineTo(xOf(i), yOf(v)) else path.moveTo(xOf(i), yOf(v))
        started = true
    }
    return path
}

private enum class Gesture { Tap, Transform, Cross }

/**
 * 主圖與副圖共用的手勢：
 * - 單指拖曳：左右平移
 * - 雙指捏合：縮放
 * - 點一下或長按：十字線，可拖著看每根 K 棒；再點一下取消
 */
fun Modifier.chartGestures(
    viewport: ChartViewport,
    barCount: () -> Int,
    selected: () -> Int?,
    onSelect: (index: Int?, y: Float?) -> Unit,
): Modifier = pointerInput(viewport) {
    val axisW = CHART_AXIS_WIDTH.toPx()

    fun indexAt(x: Float): Int = ChartWindow(barCount(), viewport, size.width - axisW).indexAt(x)

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (barCount() == 0) return@awaitEachGesture
        val crossActive = selected() != null
        var kind: Gesture? = null
        var pos = down.position
        var moved = Offset.Zero

        // 在長按時間內判斷：放開＝點擊、移動＝拖曳/縮放；超時＝長按
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (kind == null) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                when {
                    pressed.isEmpty() -> kind = Gesture.Tap
                    pressed.size > 1 -> kind = Gesture.Transform
                    else -> {
                        val c = pressed.first()
                        moved += c.positionChange()
                        pos = c.position
                        if (moved.getDistance() > viewConfiguration.touchSlop) {
                            kind = if (crossActive) Gesture.Cross else Gesture.Transform
                        }
                    }
                }
            }
        }

        when (kind ?: Gesture.Cross) {
            Gesture.Tap -> {
                if (crossActive) {
                    onSelect(null, null)
                } else {
                    onSelect(indexAt(down.position.x), down.position.y)
                }
            }

            Gesture.Cross -> {
                onSelect(indexAt(pos.x), pos.y)
                while (true) {
                    val event = awaitPointerEvent()
                    val c = event.changes.firstOrNull { it.pressed } ?: break
                    onSelect(indexAt(c.position.x), c.position.y)
                    c.consume()
                }
            }

            Gesture.Transform -> {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    val n = barCount()
                    val plotRight = size.width - axisW

                    val zoom = event.calculateZoom()
                    if (zoom != 1f) {
                        // 以兩指中心為錨點縮放
                        val cx = event.calculateCentroid(useCurrent = true).x
                        val w = plotRight / viewport.resolvedVisible(n)
                        val anchor = n - viewport.resolvedOffset(n) - (plotRight - cx) / w
                        viewport.visible = viewport.resolvedVisible(n) / zoom
                        viewport.clamp(n)
                        val w2 = plotRight / viewport.visible
                        viewport.rightOffset = n - (anchor + (plotRight - cx) / w2)
                    }

                    val pan = event.calculatePan()
                    if (pan.x != 0f) {
                        val w = plotRight / viewport.resolvedVisible(n)
                        viewport.rightOffset = viewport.resolvedOffset(n) + pan.x / w
                    }
                    viewport.clamp(n)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        }
    }
}

@Composable
fun CandleChart(
    bars: List<Candle>,
    maLines: List<SeriesLine>,
    bollinger: Bollinger?,
    showVolume: Boolean,
    showHiLo: Boolean,
    timeframe: Timeframe,
    viewport: ChartViewport,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val axisStyle = MaterialTheme.typography.labelSmall.tabular().copy(color = scheme.onSurfaceVariant)
    var crossY by remember(viewport) { mutableStateOf<Float?>(null) }

    val barCount by rememberUpdatedState(bars.size)
    val selected by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxSize()
            .chartGestures(viewport, { barCount }, { selected }) { index, y ->
                select(index)
                crossY = y
            },
    ) {
        val n = bars.size
        if (n == 0) return@Canvas

        val axisW = CHART_AXIS_WIDTH.toPx()
        val timeH = 18.dp.toPx()
        val top = 8.dp.toPx()
        val plotRight = size.width - axisW
        val chartBottom = size.height - timeH
        val volH = if (showVolume) (chartBottom - top) * 0.2f else 0f
        val mainBottom = chartBottom - if (showVolume) volH + 8.dp.toPx() else 0f
        val volTop = chartBottom - volH

        val window = ChartWindow(n, viewport, plotRight)
        val iStart = window.first
        val iEnd = window.last
        if (iEnd < iStart) return@Canvas
        val w = window.barWidth

        fun xOf(i: Int): Float = window.xOf(i)

        // ---- 價格與成交量範圍 ----
        // 先看畫面內的 K 棒；均線離得不遠才一起納入。離太遠的（例如 MA200）納入會把 K 棒壓扁，
        // 就讓它出圖，改在圖邊標價位
        var hi = Double.NEGATIVE_INFINITY
        var lo = Double.POSITIVE_INFINITY
        var maxVol = 0.0
        var hiIdx = iStart
        var loIdx = iStart
        for (i in iStart..iEnd) {
            val b = bars[i]
            if (b.high > bars[hiIdx].high) hiIdx = i
            if (b.low < bars[loIdx].low) loIdx = i
            hi = max(hi, b.high)
            lo = min(lo, b.low)
            maxVol = max(maxVol, b.volume)
        }
        if (hi <= lo) {
            hi += 1.0
            lo -= 1.0
        }
        val slack = (hi - lo) * MA_RANGE_SLACK
        val barHi = hi
        val barLo = lo
        maLines.forEach { line ->
            if (line.values.size != n) return@forEach
            var maHi = Double.NEGATIVE_INFINITY
            var maLo = Double.POSITIVE_INFINITY
            for (j in iStart..iEnd) {
                val v = line.values[j]
                if (!v.isNaN()) {
                    maHi = max(maHi, v)
                    maLo = min(maLo, v)
                }
            }
            if (maHi <= barHi + slack && maLo >= barLo - slack) {
                hi = max(hi, maHi)
                lo = min(lo, maLo)
            }
        }
        val pad = (hi - lo) * 0.06
        val pHi = hi + pad
        val pLo = lo - pad
        fun yOf(p: Double): Float = (top + (pHi - p) / (pHi - pLo) * (mainBottom - top)).toFloat()
        fun priceAt(y: Float): Double = pHi - (y - top) / (mainBottom - top) * (pHi - pLo)

        fun axisTag(text: String, y: Float, bg: Color, fg: Color) {
            val layout = measurer.measure(text, axisStyle.copy(color = fg))
            val h = layout.size.height + 4.dp.toPx()
            val ty = (y - h / 2).coerceIn(0f, max(0f, chartBottom - h))
            drawRect(bg, Offset(plotRight, ty), Size(axisW, h))
            drawText(layout, topLeft = Offset(plotRight + 4.dp.toPx(), ty + 2.dp.toPx()))
        }

        // ---- 格線與價格刻度 ----
        val grid = scheme.outlineVariant
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (k in 0..4) {
            val p = pLo + (pHi - pLo) * k / 4
            val y = yOf(p)
            drawLine(grid, Offset(0f, y), Offset(plotRight, y), strokeWidth = 1f, pathEffect = dash)
            val layout = measurer.measure(formatPrice(p), axisStyle)
            val ty = (y - layout.size.height / 2f).coerceIn(0f, max(0f, chartBottom - layout.size.height))
            drawText(layout, topLeft = Offset(plotRight + 4.dp.toPx(), ty))
        }
        if (showVolume) {
            drawLine(grid, Offset(0f, volTop), Offset(plotRight, volTop), strokeWidth = 1f)
            val layout = measurer.measure(formatLots(maxVol), axisStyle)
            drawText(layout, topLeft = Offset(plotRight + 4.dp.toPx(), volTop))
        }

        // ---- K 棒與量 ----
        val wick = max(1f, 0.8.dp.toPx())
        val bodyW = max(1f, w * 0.68f)
        clipRect(0f, 0f, plotRight, chartBottom) {
            for (i in iStart..iEnd) {
                val b = bars[i]
                val prevClose = if (i > 0) bars[i - 1].close else b.open
                val color = when {
                    b.close > b.open -> market.up
                    b.close < b.open -> market.down
                    else -> market.of(b.close - prevClose) // 十字線依昨收判斷
                }
                val x = xOf(i)
                drawLine(color, Offset(x, yOf(b.high)), Offset(x, yOf(b.low)), strokeWidth = wick)
                val yTop = yOf(max(b.open, b.close))
                val yBottom = yOf(min(b.open, b.close))
                drawRect(color, Offset(x - bodyW / 2, yTop), Size(bodyW, max(yBottom - yTop, wick)))
                if (showVolume && maxVol > 0) {
                    val vh = (b.volume / maxVol * volH).toFloat()
                    drawRect(color.copy(alpha = 0.55f), Offset(x - bodyW / 2, chartBottom - vh), Size(bodyW, vh))
                }
            }
        }
        if (showVolume) {
            drawText(measurer.measure("成交量", axisStyle), topLeft = Offset(4.dp.toPx(), volTop + 2.dp.toPx()))
        }

        // ---- 布林通道與均線 ----
        val from = max(0, iStart - 1)
        val to = min(n - 1, iEnd + 1)
        clipRect(0f, 0f, plotRight, mainBottom) {
            if (bollinger != null && bollinger.mid.size == n) {
                val band = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                val thin = 1.dp.toPx()
                drawPath(linePath(bollinger.upper, from, to, ::xOf, ::yOf), band, style = Stroke(width = thin))
                drawPath(linePath(bollinger.lower, from, to, ::xOf, ::yOf), band, style = Stroke(width = thin))
                drawPath(linePath(bollinger.mid, from, to, ::xOf, ::yOf), band, style = Stroke(width = thin, pathEffect = dash))
            }
            maLines.forEach { line ->
                if (line.values.size != n) return@forEach
                drawPath(linePath(line.values, from, to, ::xOf, ::yOf), line.color, style = Stroke(width = 1.3.dp.toPx()))
            }
        }

        // ---- 畫面內最高／最低 ----
        fun marker(i: Int, price: Double) {
            val x = xOf(i)
            if (x < 0f || x > plotRight) return
            val leftHalf = x < plotRight / 2
            val text = if (leftHalf) "← ${formatPrice(price)}" else "${formatPrice(price)} →"
            val layout = measurer.measure(text, axisStyle.copy(color = scheme.onSurface))
            val tx = if (leftHalf) x + 2.dp.toPx() else x - 2.dp.toPx() - layout.size.width
            val ty = (yOf(price) - layout.size.height / 2f).coerceIn(0f, max(0f, mainBottom - layout.size.height))
            drawText(layout, topLeft = Offset(tx, ty))
        }
        if (showHiLo) {
            marker(hiIdx, bars[hiIdx].high)
            marker(loIdx, bars[loIdx].low)
        }

        // ---- 出圖的均線：在右側標出畫面最右一根的均線價位與方向 ----
        val tagGap = 2.dp.toPx()
        val tagPad = 3.dp.toPx()
        var aboveY = top + tagGap
        var belowY = mainBottom - tagGap
        maLines.forEach { line ->
            if (line.values.size != n) return@forEach
            val v = line.values[iEnd]
            if (v.isNaN() || v in pLo..pHi) return@forEach
            val above = v > pHi
            val layout = measurer.measure(
                "${line.label} ${formatPrice(v)} ${if (above) "↑" else "↓"}",
                axisStyle.copy(color = line.color),
            )
            val tagW = layout.size.width + tagPad * 2
            val tagH = layout.size.height.toFloat()
            val ty = if (above) {
                aboveY.also { aboveY += tagH + tagGap }
            } else {
                belowY -= tagH
                belowY.also { belowY -= tagGap }
            }
            val tx = plotRight - tagW - tagGap
            drawRect(scheme.background.copy(alpha = 0.85f), Offset(tx, ty), Size(tagW, tagH))
            drawText(layout, topLeft = Offset(tx + tagPad, ty))
        }

        // ---- 最新價虛線 ----
        val last = bars[n - 1]
        val lastY = yOf(last.close)
        if (lastY in top..mainBottom) {
            val prev = if (n > 1) bars[n - 2].close else last.open
            val c = market.of(last.close - prev)
            drawLine(
                c.copy(alpha = 0.7f),
                Offset(0f, lastY),
                Offset(plotRight, lastY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
            axisTag(formatPrice(last.close), lastY, c, onColorFor(c))
        }

        // ---- 時間刻度（對齊固定間隔，平移時不會跳）----
        val every = max(1, ((iEnd - iStart + 1) / 4.0).roundToInt())
        var i = iEnd - iEnd % every
        while (i >= iStart) {
            val layout = measurer.measure(formatBarTime(bars[i].time, timeframe, full = false), axisStyle)
            val x = (xOf(i) - layout.size.width / 2f).coerceIn(0f, max(0f, plotRight - layout.size.width))
            drawText(layout, topLeft = Offset(x, chartBottom + 3.dp.toPx()))
            i -= every
        }

        // ---- 十字線 ----
        val sel = selectedIndex
        if (sel != null && sel in 0 until n) {
            val x = xOf(sel)
            if (x in 0f..plotRight) {
                val lineColor = scheme.onSurface.copy(alpha = 0.5f)
                drawLine(lineColor, Offset(x, top), Offset(x, chartBottom), strokeWidth = 1f)
                crossY?.let { cy ->
                    if (cy in top..mainBottom) {
                        drawLine(lineColor, Offset(0f, cy), Offset(plotRight, cy), strokeWidth = 1f)
                        axisTag(formatPrice(priceAt(cy)), cy, scheme.inverseSurface, scheme.inverseOnSurface)
                    }
                }
                val layout = measurer.measure(
                    formatBarTime(bars[sel].time, timeframe, full = true),
                    axisStyle.copy(color = scheme.inverseOnSurface),
                )
                val boxW = layout.size.width + 8.dp.toPx()
                val bx = (x - boxW / 2).coerceIn(0f, max(0f, size.width - boxW))
                drawRect(scheme.inverseSurface, Offset(bx, chartBottom), Size(boxW, timeH))
                drawText(layout, topLeft = Offset(bx + 4.dp.toPx(), chartBottom + 3.dp.toPx()))
            }
        }
    }
}
