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
import tw.stockpeek.data.Candle
import tw.stockpeek.data.Timeframe
import tw.stockpeek.ui.theme.LocalMarketColors
import tw.stockpeek.ui.theme.MaColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_VISIBLE = 12f
private const val MAX_VISIBLE = 300f
private val AXIS_WIDTH = 52.dp

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

private enum class Gesture { Tap, Transform, Cross }

/**
 * 手勢：
 * - 單指拖曳：左右平移
 * - 雙指捏合：縮放
 * - 點一下或長按：十字線，可拖著看每根 K 棒；再點一下取消
 */
@Composable
fun CandleChart(
    bars: List<Candle>,
    maSeries: List<DoubleArray>,
    showVolume: Boolean,
    timeframe: Timeframe,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewport = remember(timeframe) {
        ChartViewport(
            when (timeframe) {
                Timeframe.MIN5 -> 54f   // 一天 54 根 5 分 K
                Timeframe.DAY -> 60f
                Timeframe.WEEK -> 52f
                Timeframe.MONTH -> 48f
            },
        )
    }
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = scheme.onSurfaceVariant,
        fontFeatureSettings = "tnum",
    )
    var crossY by remember(timeframe) { mutableStateOf<Float?>(null) }

    val barCount by rememberUpdatedState(bars.size)
    val selected by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(viewport) {
                val axisW = AXIS_WIDTH.toPx()

                fun indexAt(x: Float): Int {
                    val n = barCount
                    val plotRight = size.width - axisW
                    val visible = viewport.resolvedVisible(n)
                    val w = plotRight / visible
                    val endF = n - viewport.resolvedOffset(n)
                    val first = max(0, floor(endF - visible).toInt())
                    val last = max(first, min(n - 1, ceil(endF).toInt() - 1))
                    return (endF - 0.5f - (plotRight - x) / w).roundToInt().coerceIn(first, last)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (barCount == 0) return@awaitEachGesture
                    val crossActive = selected != null
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
                                select(null)
                                crossY = null
                            } else {
                                select(indexAt(down.position.x))
                                crossY = down.position.y
                            }
                        }

                        Gesture.Cross -> {
                            select(indexAt(pos.x))
                            crossY = pos.y
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.pressed } ?: break
                                select(indexAt(c.position.x))
                                crossY = c.position.y
                                c.consume()
                            }
                        }

                        Gesture.Transform -> {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val n = barCount
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
            },
    ) {
        val n = bars.size
        if (n == 0) return@Canvas

        val axisW = AXIS_WIDTH.toPx()
        val timeH = 18.dp.toPx()
        val top = 8.dp.toPx()
        val plotRight = size.width - axisW
        val chartBottom = size.height - timeH
        val volH = if (showVolume) (chartBottom - top) * 0.2f else 0f
        val mainBottom = chartBottom - if (showVolume) volH + 8.dp.toPx() else 0f
        val volTop = chartBottom - volH

        val visible = viewport.resolvedVisible(n)
        val endF = n - viewport.resolvedOffset(n)
        val w = plotRight / visible
        val iStart = max(0, floor(endF - visible).toInt())
        val iEnd = min(n - 1, ceil(endF).toInt() - 1)
        if (iEnd < iStart) return@Canvas

        fun xOf(i: Int): Float = plotRight - (endF - i - 0.5f) * w

        // ---- 價格與成交量範圍（只看畫面內）----
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
            for (ma in maSeries) {
                if (ma.size != n) continue
                val v = ma[i]
                if (!v.isNaN()) {
                    hi = max(hi, v)
                    lo = min(lo, v)
                }
            }
        }
        if (hi <= lo) {
            hi += 1.0
            lo -= 1.0
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
        for (k in 0..4) {
            val p = pLo + (pHi - pLo) * k / 4
            val y = yOf(p)
            drawLine(grid, Offset(0f, y), Offset(plotRight, y), strokeWidth = 1f)
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

        // ---- 均線 ----
        clipRect(0f, 0f, plotRight, mainBottom) {
            maSeries.forEachIndexed { k, ma ->
                if (ma.size != n) return@forEachIndexed
                val path = Path()
                var started = false
                for (i in max(0, iStart - 1)..min(n - 1, iEnd + 1)) {
                    val v = ma[i]
                    if (v.isNaN()) {
                        started = false
                        continue
                    }
                    val x = xOf(i)
                    val y = yOf(v)
                    if (started) {
                        path.lineTo(x, y)
                    } else {
                        path.moveTo(x, y)
                        started = true
                    }
                }
                drawPath(path, MaColors[k % MaColors.size], style = Stroke(width = 1.3.dp.toPx()))
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
        marker(hiIdx, bars[hiIdx].high)
        marker(loIdx, bars[loIdx].low)

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
            axisTag(formatPrice(last.close), lastY, c, Color.White)
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
