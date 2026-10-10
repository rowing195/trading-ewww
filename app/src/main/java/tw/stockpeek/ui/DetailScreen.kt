package tw.stockpeek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import tw.stockpeek.AppViewModel
import tw.stockpeek.data.Candle
import tw.stockpeek.data.MarketClock
import tw.stockpeek.data.Quote
import tw.stockpeek.data.SubIndicator
import tw.stockpeek.data.TechSummary
import tw.stockpeek.data.Timeframe
import tw.stockpeek.data.analyzeDaily
import tw.stockpeek.data.bollinger
import tw.stockpeek.data.movingAverage
import tw.stockpeek.ui.theme.LocalMaColors
import tw.stockpeek.ui.theme.LocalMarketColors
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, symbol: String, onBack: () -> Unit, onSummary: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val tick by vm.refreshTick.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val quote = quotes[symbol]
    val name = quote?.name ?: settings.watchlist.firstOrNull { it.symbol == symbol }?.name ?: symbol
    val maColors = LocalMaColors.current

    var tf by rememberSaveable { mutableStateOf(Timeframe.DAY) }
    var candles by remember(symbol, tf) { mutableStateOf<List<Candle>?>(null) }
    var error by remember(symbol, tf) { mutableStateOf<String?>(null) }
    var selected by remember(symbol, tf) { mutableStateOf<Int?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var hiddenMa by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var subTab by rememberSaveable { mutableStateOf(SubIndicator.KD) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val viewport = remember(symbol, tf) { ChartViewport(initialVisibleBars(tf)) }

    // 5 分 K 跟著首頁的報價更新一起刷新；日／週／月 K 有 5 分鐘快取
    val minuteTick = if (tf == Timeframe.MIN5) tick else 0
    LaunchedEffect(symbol, tf, minuteTick, reload) {
        error = null
        try {
            candles = vm.candles(symbol, tf, force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = vm.friendly(e)
        }
    }

    // 技術面摘要固定看日 K；K 線停在其他週期時另外抓（同樣有 5 分鐘快取）
    var dayCandles by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    LaunchedEffect(symbol, tf) {
        if (tf == Timeframe.DAY) return@LaunchedEffect
        try {
            dayCandles = vm.candles(symbol, Timeframe.DAY, force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 摘要只是輔助資訊，抓不到就不顯示
        }
    }
    val dailySource = if (tf == Timeframe.DAY) candles else dayCandles
    val summary = remember(dailySource, quote) { dailySource?.let { analyzeDaily(mergeLiveBar(it, quote)) } }

    // 日 K 最後一根用即時報價補上（歷史資料盤後 16:30 才更新）
    val bars = remember(candles, quote, tf) {
        val c = candles
        if (c != null && tf == Timeframe.DAY) mergeLiveBar(c, quote) else c
    }
    val closes = remember(bars) { bars.orEmpty().map { it.close } }
    val maSeries = remember(closes, settings.maPeriods) { settings.maPeriods.map { movingAverage(closes, it) } }
    val maLines = settings.maPeriods.mapIndexedNotNull { k, p ->
        if (p in hiddenMa) null else SeriesLine("MA$p", maSeries[k], maColors[k % maColors.size])
    }
    val bands = remember(closes, settings.showBollinger) {
        if (settings.showBollinger && closes.isNotEmpty()) bollinger(closes) else null
    }
    val sub = subTab.takeIf { it in settings.subIndicators } ?: settings.subIndicators.firstOrNull()
    val subSeries = remember(bars, sub, maColors) {
        val b = bars
        if (b.isNullOrEmpty() || sub == null) null else subSeriesOf(b, sub, maColors[0], maColors[1])
    }
    val list = bars.orEmpty()
    val idx = (selected ?: list.lastIndex).takeIf { it in list.indices }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(
                            symbol,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSheet = true }) {
                        Icon(AppIcons.Tune, contentDescription = "指標設定")
                    }
                    IconButton(onClick = {
                        vm.refreshQuotes()
                        reload++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新整理")
                    }
                },
            )
        },
        bottomBar = { BrokerBar(settings.brokers, copySymbol = symbol, onManage = null) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            QuoteHeader(quote, lastUpdated)

            SegmentedControl(
                options = Timeframe.entries,
                selected = tf,
                label = { it.label },
                onSelect = { tf = it },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(),
            )

            BarInfo(
                bars = list,
                idx = idx,
                tf = tf,
                periods = settings.maPeriods,
                maSeries = maSeries,
                hidden = hiddenMa,
                onToggleMa = { p -> hiddenMa = if (p in hiddenMa) hiddenMa - p else hiddenMa + p },
                showLatest = selected != null,
                onLatest = { selected = null },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp),
            ) {
                when {
                    bars == null && error == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    bars.isNullOrEmpty() && error != null -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        AppTextButton("重試", onClick = { reload++ })
                    }
                    bars.isNullOrEmpty() -> Text(
                        "這個區間沒有資料",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> CandleChart(
                        bars = list,
                        maLines = maLines,
                        bollinger = bands,
                        showVolume = settings.showVolume,
                        showHiLo = settings.showHiLo,
                        timeframe = tf,
                        viewport = viewport,
                        selectedIndex = selected,
                        onSelect = { selected = it },
                    )
                }
            }

            if (sub != null && subSeries != null) {
                SubHeader(
                    options = settings.subIndicators,
                    selected = sub,
                    onSelect = { subTab = it },
                    series = subSeries,
                    idx = idx,
                )
                IndicatorChart(
                    bars = list,
                    series = subSeries,
                    viewport = viewport,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(start = 8.dp),
                )
            }

            summary?.let { SignalStrip(it, onClick = onSummary) }
        }
    }

    if (showSheet) {
        IndicatorSheet(vm = vm, settings = settings, onDismiss = { showSheet = false })
    }
}

fun mergeLiveBar(candles: List<Candle>, quote: Quote?): List<Candle> {
    if (quote == null || quote.date.isBlank()) return candles
    val close = quote.price ?: return candles
    val open = quote.open ?: return candles
    val live = Candle(
        time = quote.date,
        open = open,
        high = quote.high ?: maxOf(open, close),
        low = quote.low ?: minOf(open, close),
        close = close,
        volume = quote.volumeLots?.toDouble() ?: 0.0,
    )
    val last = candles.lastOrNull() ?: return listOf(live)
    return when {
        last.time == live.time -> candles.dropLast(1) + live
        last.time < live.time -> candles + live
        else -> candles
    }
}

@Composable
private fun QuoteHeader(quote: Quote?, lastUpdated: ZonedDateTime?) {
    val market = LocalMarketColors.current
    val scheme = MaterialTheme.colorScheme
    val color = market.of(quote?.change)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                formatPrice(quote?.price ?: quote?.reference),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp).tabular(),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatChangeArrow(quote?.change), style = MaterialTheme.typography.titleSmall.tabular(), color = color)
                Text(formatPercent(quote?.changePercent), style = MaterialTheme.typography.titleSmall.tabular(), color = color)
                when {
                    quote?.isLimitUp == true -> SolidBadge("漲停", market.up)
                    quote?.isLimitDown == true -> SolidBadge("跌停", market.down)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val active = MarketClock.isActive()
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active) scheme.primary else scheme.onSurfaceVariant),
                )
                val parts = listOfNotNull(
                    MarketClock.statusLabel(),
                    if (active) lastUpdated?.format(timeFmt) else quote?.date?.ifBlank { null },
                    "試撮".takeIf { quote?.isTrial == true },
                )
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        val ref = quote?.reference
        val high = quote?.high
        val low = quote?.low
        val amplitude = if (high != null && low != null && ref != null && ref > 0) (high - low) / ref * 100 else null
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                StatCell("開", formatPrice(quote?.open), market.of(diff(quote?.open, ref)))
                StatCell("高", formatPrice(high), market.of(diff(high, ref)))
                StatCell("低", formatPrice(low), market.of(diff(low, ref)))
            }
            Row {
                StatCell("參考", formatPrice(ref), null)
                StatCell("量（張）", formatLots(quote?.volumeLots?.toDouble()), null)
                StatCell("振幅", amplitude?.let { formatFixed(it, 2) + "%" } ?: "--", null)
            }
        }
    }
}

private fun diff(a: Double?, b: Double?): Double? = if (a != null && b != null) a - b else null

@Composable
private fun RowScope.StatCell(label: String, value: String, color: Color?) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 「開 970」這種小標＋數值。 */
@Composable
private fun LabeledValue(
    label: String,
    value: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
        Spacer(Modifier.width(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = valueColor,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** K 線上方的資訊列：十字線選到哪根就顯示哪根，沒選就顯示最新一根；下面是可點的均線開關。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarInfo(
    bars: List<Candle>,
    idx: Int?,
    tf: Timeframe,
    periods: List<Int>,
    maSeries: List<DoubleArray>,
    hidden: Set<Int>,
    onToggleMa: (Int) -> Unit,
    showLatest: Boolean,
    onLatest: () -> Unit,
) {
    val market = LocalMarketColors.current
    val maColors = LocalMaColors.current
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (idx != null) {
                    val b = bars[idx]
                    val prev = if (idx > 0) bars[idx - 1].close else b.open
                    val c = market.of(b.close - prev)
                    Text(formatBarTime(b.time, tf, full = true), style = MaterialTheme.typography.labelMedium.tabular())
                    LabeledValue("開", formatPrice(b.open))
                    LabeledValue("高", formatPrice(b.high))
                    LabeledValue("低", formatPrice(b.low))
                    LabeledValue("收", formatPrice(b.close), valueColor = c, bold = true)
                    Text(
                        formatPercent(if (prev != 0.0) (b.close - prev) / prev * 100 else null),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = c,
                    )
                    LabeledValue("量", formatLots(b.volume))
                }
            }
            if (showLatest) {
                Text(
                    "最新",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "關閉十字線，回到最新一根", onClick = onLatest)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (periods.isNotEmpty()) {
            // 均線多、價格位數多時一行放不下，換行顯示
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                periods.forEachIndexed { k, p ->
                    val color = maColors[k % maColors.size]
                    val off = p in hidden
                    val v = idx?.let { maSeries.getOrNull(k)?.getOrNull(it) }
                    val decoration = if (off) TextDecoration.LineThrough else TextDecoration.None
                    Row(
                        Modifier
                            .alpha(if (off) 0.4f else 1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(scheme.surfaceContainer)
                            .clickable(role = Role.Switch, onClick = { onToggleMa(p) })
                            .semantics { stateDescription = if (off) "已隱藏" else "顯示中" }
                            .height(28.dp)
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier
                                .size(width = 8.dp, height = 2.dp)
                                .background(color),
                        )
                        Text(
                            "MA$p",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = color,
                            textDecoration = decoration,
                        )
                        Text(
                            if (v == null || v.isNaN()) "--" else formatPrice(v),
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            textDecoration = decoration,
                        )
                    }
                }
            }
        }
    }
}

/** 副圖標題列：KD／MACD／RSI 切換與目前數值。 */
@Composable
private fun SubHeader(
    options: List<SubIndicator>,
    selected: SubIndicator,
    onSelect: (SubIndicator) -> Unit,
    series: SubSeries,
    idx: Int?,
) {
    val market = LocalMarketColors.current
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedControl(
            options = options,
            selected = selected,
            label = { it.label },
            onSelect = onSelect,
            equalWidth = false,
            itemHeight = 30.dp,
        )
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (idx != null) {
                series.lines.forEach { line ->
                    LabeledValue(line.label, formatFixed(line.values.getOrNull(idx), series.decimals), labelColor = line.color)
                }
                series.histogram?.getOrNull(idx)?.let { v ->
                    LabeledValue("OSC", formatFixed(v, series.decimals), valueColor = market.of(v))
                }
            }
        }
    }
}

/** 技術面摘要入口：判讀等級＋三個重點，點了進摘要頁。 */
@Composable
private fun SignalStrip(summary: TechSummary, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.surfaceVariant, shape)
            .clickable(onClickLabel = "查看技術面摘要", onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("技術面", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        TintPill(summary.label, levelColor(summary.level))
        Text(
            summary.highlights.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}
