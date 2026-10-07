package tw.stockpeek.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import tw.stockpeek.AppViewModel
import tw.stockpeek.data.Candle
import tw.stockpeek.data.Quote
import tw.stockpeek.data.Timeframe
import tw.stockpeek.data.movingAverage
import tw.stockpeek.ui.theme.LocalMarketColors
import tw.stockpeek.ui.theme.MaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, symbol: String, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val tick by vm.refreshTick.collectAsStateWithLifecycle()
    val quote = quotes[symbol]
    val name = quote?.name ?: settings.watchlist.firstOrNull { it.symbol == symbol }?.name ?: symbol

    var tf by rememberSaveable { mutableStateOf(Timeframe.DAY) }
    var candles by remember(symbol, tf) { mutableStateOf<List<Candle>?>(null) }
    var error by remember(symbol, tf) { mutableStateOf<String?>(null) }
    var selected by remember(symbol, tf) { mutableStateOf<Int?>(null) }
    var reload by remember { mutableIntStateOf(0) }

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

    // 日 K 最後一根用即時報價補上（歷史資料盤後 16:30 才更新）
    val bars = remember(candles, quote, tf) {
        val c = candles
        if (c != null && tf == Timeframe.DAY) mergeLiveBar(c, quote) else c
    }
    val maSeries = remember(bars, settings.maPeriods) {
        val closes = bars.orEmpty().map { it.close }
        settings.maPeriods.map { movingAverage(closes, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(
                            symbol,
                            style = MaterialTheme.typography.labelMedium,
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
            QuoteHeader(quote)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Timeframe.entries.forEach { t ->
                    FilterChip(
                        selected = tf == t,
                        onClick = { tf = t },
                        label = { Text(t.label) },
                    )
                }
            }

            BarInfo(bars, selected, tf, settings.maPeriods, maSeries)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
            ) {
                val data = bars
                when {
                    data == null && error == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    data.isNullOrEmpty() && error != null -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload++ }) { Text("重試") }
                    }
                    data.isNullOrEmpty() -> Text(
                        "這個區間沒有資料",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> CandleChart(
                        bars = data.orEmpty(),
                        maSeries = maSeries,
                        maPeriods = settings.maPeriods,
                        showVolume = settings.showVolume,
                        timeframe = tf,
                        selectedIndex = selected,
                        onSelect = { selected = it },
                    )
                }
            }
        }
    }
}

private fun mergeLiveBar(candles: List<Candle>, quote: Quote?): List<Candle> {
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
private fun QuoteHeader(quote: Quote?) {
    val market = LocalMarketColors.current
    val color = market.of(quote?.change)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatPrice(quote?.price ?: quote?.reference),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${formatChange(quote?.change)}  ${formatPercent(quote?.changePercent)}",
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = color,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            val tag = when {
                quote == null -> ""
                quote.isLimitUp -> "漲停"
                quote.isLimitDown -> "跌停"
                quote.isTrial -> "試撮"
                else -> quote.date
            }
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Stat("開", formatPrice(quote?.open), market.of(diff(quote?.open, quote?.reference)))
            Stat("高", formatPrice(quote?.high), market.of(diff(quote?.high, quote?.reference)))
            Stat("低", formatPrice(quote?.low), market.of(diff(quote?.low, quote?.reference)))
            Stat("參考", formatPrice(quote?.reference), null)
            Stat("量", formatLots(quote?.volumeLots?.toDouble()), null)
        }
    }
}

private fun diff(a: Double?, b: Double?): Double? = if (a != null && b != null) a - b else null

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** K 線上方的資訊列：十字線選到哪根就顯示哪根，沒選就顯示最新一根。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarInfo(
    bars: List<Candle>?,
    selected: Int?,
    tf: Timeframe,
    periods: List<Int>,
    maSeries: List<DoubleArray>,
) {
    val market = LocalMarketColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.tabular()
    val list = bars.orEmpty()
    val idx = (selected ?: list.lastIndex).takeIf { it in list.indices }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (idx == null) {
            Text(" ", style = style)
            Text(" ", style = style)
            Text(" ", style = style)
        } else {
            val b = list[idx]
            val prev = if (idx > 0) list[idx - 1].close else b.open
            val c = market.of(b.close - prev)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatBarTime(b.time, tf, full = true), style = style, color = muted)
                Text("收 ${formatPrice(b.close)}", style = style, color = c)
                Text(
                    formatPercent(if (prev != 0.0) (b.close - prev) / prev * 100 else null),
                    style = style,
                    color = c,
                )
                Text("量 ${formatLots(b.volume)}", style = style, color = muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("開 ${formatPrice(b.open)}", style = style)
                Text("高 ${formatPrice(b.high)}", style = style)
                Text("低 ${formatPrice(b.low)}", style = style)
            }
            // 均線多、價格位數多時一行放不下，換行顯示
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                periods.forEachIndexed { k, p ->
                    val v = maSeries.getOrNull(k)?.getOrNull(idx)
                    Text(
                        "MA$p ${if (v == null || v.isNaN()) "--" else formatPrice(v)}",
                        style = style,
                        color = MaColors[k % MaColors.size],
                    )
                }
            }
        }
    }
}
