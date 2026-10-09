package tw.stockpeek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import tw.stockpeek.AppViewModel
import tw.stockpeek.data.Candle
import tw.stockpeek.data.LEVEL_LABELS
import tw.stockpeek.data.MIN_SUMMARY_BARS
import tw.stockpeek.data.MarketClock
import tw.stockpeek.data.Signal
import tw.stockpeek.data.TechSummary
import tw.stockpeek.data.Timeframe
import tw.stockpeek.data.analyzeDaily
import tw.stockpeek.data.movingAverage
import tw.stockpeek.ui.theme.LocalMaColors
import tw.stockpeek.ui.theme.LocalMarketColors
import kotlin.math.abs
import kotlin.math.max

/** 技術面摘要頁：固定看日 K。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalsScreen(vm: AppViewModel, symbol: String, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val quote = quotes[symbol]
    val name = quote?.name ?: settings.watchlist.firstOrNull { it.symbol == symbol }?.name ?: symbol

    var candles by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    var error by remember(symbol) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(symbol, reload) {
        error = null
        try {
            candles = vm.candles(symbol, Timeframe.DAY, force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = vm.friendly(e)
        }
    }
    val bars = remember(candles, quote) { candles?.let { mergeLiveBar(it, quote) } }
    val summary = remember(bars) { bars?.let(::analyzeDaily) }
    val maValues = remember(bars, settings.maPeriods) {
        val closes = bars.orEmpty().map { it.close }
        settings.maPeriods.map { p -> p to (movingAverage(closes, p).lastOrNull() ?: Double.NaN) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("技術面摘要", fontWeight = FontWeight.SemiBold)
                        Text(
                            "$name $symbol · 日K",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回 K 線")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                bars == null && error == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                bars == null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { reload++ }) { Text("重試") }
                }
                summary == null -> Text(
                    "日 K 不到 $MIN_SUMMARY_BARS 根，暫時無法判讀",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> SummaryContent(summary, maValues)
            }
        }
    }
}

@Composable
private fun SummaryContent(s: TechSummary, maValues: List<Pair<Int, Double>>) {
    val market = LocalMarketColors.current
    val maColors = LocalMaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        VerdictCard(s)

        SectionHeader("趨勢 · 均線", s.alignment)
        if (maValues.isNotEmpty()) {
            ListCard {
                maValues.forEachIndexed { k, (p, v) ->
                    if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    MaRow(p, v, s.close, maColors[k % maColors.size])
                }
            }
        }
        Note(s.alignment.note, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        SectionHeader("動能", null)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OscillatorCard(
                title = "KD",
                params = "9, 3, 3",
                signal = s.kdSignal,
                values = listOf(Triple("K", s.k, maColors[0]), Triple("D", s.d, maColors[1])),
            )
            MacdCard(s)
            OscillatorCard(
                title = "RSI",
                params = "6, 12",
                signal = s.rsiSignal,
                values = listOf(Triple("RSI6", s.rsi6, maColors[0]), Triple("RSI12", s.rsi12, maColors[1])),
            )
        }

        SectionHeader("量能", s.volumeSignal)
        Card {
            val maxVolume = listOf(s.volume, s.volumeMa5, s.volumeMa20).filter { !it.isNaN() }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val avgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            VolumeBar("今日", s.volume, maxVolume, market.of(s.close - s.prevClose))
            VolumeBar("5 日均量", s.volumeMa5, maxVolume, avgColor)
            VolumeBar("20 日均量", s.volumeMa20, maxVolume, avgColor)
            val note = s.volumeSignal.note + if (MarketClock.isActive()) "（盤中為目前累計量）" else ""
            Note(note)
        }

        SectionHeader("關鍵價位", null)
        KeyLevels(s)

        Text(
            "以上依歷史價格與成交量自動計算，只呈現指標目前的狀態，不構成任何投資建議。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 24.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun VerdictCard(s: TechSummary) {
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    val color = levelColor(s.level)
    Card(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("綜合判讀", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(
                    s.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Count("多方", s.signals.count { it.bias > 0 }, market.up)
                Count("中性", s.signals.count { it.bias == 0 }, scheme.onSurface)
                Count("空方", s.signals.count { it.bias < 0 }, market.down)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LEVEL_LABELS.indices.forEach { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == s.level) levelColor(i) else scheme.surfaceVariant),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LEVEL_LABELS.forEachIndexed { i, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (i == s.level) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == s.level) levelColor(i) else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            "依 8 項日 K 指標計算 · 收盤 ${formatPrice(s.close)}",
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Count(label: String, n: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$n", style = MaterialTheme.typography.titleLarge.tabular(), fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SectionHeader(title: String, signal: Signal?) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (signal != null) TintPill(signal.state, signalColor(signal.bias, signal.caution))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = modifier,
    )
}

private fun maAlias(period: Int): String = when (period) {
    5 -> "週線"
    10 -> "雙週線"
    20 -> "月線"
    60 -> "季線"
    120 -> "半年線"
    240 -> "年線"
    else -> "$period 日線"
}

@Composable
private fun MaRow(period: Int, value: Double, close: Double, color: Color) {
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 14.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text("MA$period", style = MaterialTheme.typography.labelLarge.tabular(), modifier = Modifier.width(52.dp))
        Text(
            maAlias(period),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (value.isNaN()) {
            TintPill("資料不足", scheme.onSurfaceVariant)
        } else {
            val above = close >= value
            val c = if (above) market.up else market.down
            Text(formatPrice(value), style = MaterialTheme.typography.labelLarge.tabular())
            Text(
                formatPercent((close / value - 1) * 100),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = c,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp),
            )
            TintPill(if (above) "站上" else "跌破", c)
        }
    }
}

@Composable
private fun CardHeader(title: String, params: String, signal: Signal) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(params, style = MaterialTheme.typography.labelSmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        TintPill(signal.state, signalColor(signal.bias, signal.caution))
    }
}

@Composable
private fun BigValue(label: String, value: String, labelColor: Color, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.padding(bottom = 3.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.tabular(), fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** KD、RSI：0–100 的刻度條，20 以下與 80 以上加深，標出各線目前位置。 */
@Composable
private fun OscillatorCard(title: String, params: String, signal: Signal, values: List<Triple<String, Double, Color>>) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant, fontFeatureSettings = "tnum")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardHeader(title, params, signal)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            values.forEach { (label, v, color) -> BigValue(label, formatFixed(v, 1), color) }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp),
        ) {
            val w = size.width
            val trackTop = 6.dp.toPx()
            val trackH = 8.dp.toPx()
            val r = CornerRadius(trackH / 2)
            drawRoundRect(scheme.surfaceVariant, Offset(0f, trackTop), Size(w, trackH), r)
            drawRoundRect(scheme.outlineVariant, Offset(0f, trackTop), Size(w * 0.2f, trackH), r)
            drawRoundRect(scheme.outlineVariant, Offset(w * 0.8f, trackTop), Size(w * 0.2f, trackH), r)
            values.forEach { (_, v, color) ->
                if (v.isNaN()) return@forEach
                val x = (v.coerceIn(0.0, 100.0) / 100 * w).toFloat()
                drawRoundRect(color, Offset(x - 1.5.dp.toPx(), 1.dp.toPx()), Size(3.dp.toPx(), 18.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            }
            listOf(20, 80).forEach { mark ->
                val layout = measurer.measure("$mark", labelStyle)
                drawText(layout, topLeft = Offset(w * mark / 100 - layout.size.width / 2f, 21.dp.toPx()))
            }
        }
        Note(signal.note)
    }
}

@Composable
private fun MacdCard(s: TechSummary) {
    val market = LocalMarketColors.current
    val maColors = LocalMaColors.current
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardHeader("MACD", "12, 26, 9", s.macdSignal)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BigValue("DIF", formatFixed(s.dif, 2), maColors[0])
            BigValue("MACD", formatFixed(s.signalLine, 2), maColors[1])
            BigValue("OSC", formatFixed(s.osc, 2), scheme.onSurfaceVariant, market.of(s.osc))
        }
        // 近 30 天柱狀體
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            val values = s.oscRecent
            if (values.isEmpty()) return@Canvas
            val mid = size.height / 2
            drawLine(scheme.outlineVariant, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
            val amp = values.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
            val step = size.width / values.size
            val barW = max(1f, step * 0.65f)
            values.forEachIndexed { i, v ->
                val h = max(1f, (abs(v) / amp * (mid - 2.dp.toPx())).toFloat())
                val color = (if (v >= 0) market.up else market.down).copy(alpha = if (i == values.lastIndex) 1f else 0.6f)
                drawRect(color, Offset(i * step + (step - barW) / 2, if (v >= 0) mid - h else mid), Size(barW, h))
            }
        }
        Note(s.macdSignal.note)
    }
}

@Composable
private fun VolumeBar(label: String, value: Double, max: Double, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                if (value.isNaN()) "--" else "${formatLots(value)} 張",
                style = MaterialTheme.typography.labelMedium.tabular(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!value.isNaN()) {
                Box(
                    Modifier
                        .fillMaxWidth((value / max).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
        }
    }
}

/** 由高到低列出近期高低點、月線與現價，標示壓力或支撐。 */
@Composable
private fun KeyLevels(s: TechSummary) {
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    val levels = listOf(
        "60 日高點" to s.high60,
        "20 日高點" to s.high20,
        "月線 MA20" to s.ma20,
        "現價" to s.close,
        "20 日低點" to s.low20,
        "60 日低點" to s.low60,
    ).sortedByDescending { it.second }
    val nowColor = market.of(s.close - s.prevClose)
    ListCard {
        levels.forEachIndexed { k, (label, price) ->
            if (k > 0) HorizontalDivider(color = scheme.surfaceContainerHigh)
            val current = label == "現價"
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (current) scheme.surfaceContainerHigh else Color.Transparent)
                    .heightIn(min = 46.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (current) nowColor else scheme.outline.copy(alpha = 0.5f)),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (!current) {
                    Text(
                        when {
                            price > s.close -> "壓力"
                            price < s.close -> "支撐"
                            else -> "—"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatPrice(price),
                    style = MaterialTheme.typography.titleSmall.tabular(),
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) nowColor else scheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(76.dp),
                )
                Text(
                    if (current) "" else formatPercent((price / s.close - 1) * 100),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(60.dp),
                )
            }
        }
    }
}
