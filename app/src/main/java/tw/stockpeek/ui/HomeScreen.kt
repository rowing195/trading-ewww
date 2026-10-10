package tw.stockpeek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tw.stockpeek.AppViewModel
import tw.stockpeek.data.MarketClock
import tw.stockpeek.data.Quote
import tw.stockpeek.data.SortMode
import tw.stockpeek.data.WatchItem
import tw.stockpeek.ui.theme.LocalMarketColors
import tw.stockpeek.ui.theme.onColorFor
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage(text)
    }

    val rows = remember(settings.watchlist, quotes, settings.sortMode) {
        sortRows(settings.watchlist, quotes, settings.sortMode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("我的股票", fontWeight = FontWeight.SemiBold)
                        val updated = lastUpdated?.let { " · 更新 ${it.format(timeFmt)}" } ?: ""
                        Text(
                            MarketClock.statusLabel() + updated,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { sortMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            Icon(AppIcons.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(settings.sortMode.label)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        vm.setSortMode(mode)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    if (settings.hasApiKey) {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "新增股票")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        bottomBar = { BrokerBar(settings.brokers, copySymbol = null, onManage = onSettings) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshQuotes() },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !settings.loaded -> Unit
                !settings.hasApiKey -> EmptyState(
                    title = "先設定行情金鑰",
                    body = "報價與 K 線來自富果行情 API，免費註冊就有金鑰（每分鐘 60 次）。設定一次之後打開就能看，不需要登入任何帳號。",
                    action = "前往設定",
                    onAction = onSettings,
                )
                settings.watchlist.isEmpty() -> EmptyState(
                    title = "還沒有股票",
                    body = "按右上角 + 輸入代號或名稱，可以一次貼上好幾檔，例如「2330 0050 00878 鴻海」。",
                    action = "新增股票",
                    onAction = { showAdd = true },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "summary") { Summary(rows.map { it.second }) }
                    item(key = "header") { ListHeader() }
                    items(rows, key = { it.first.symbol }) { (item, quote) ->
                        StockRow(
                            item = item,
                            quote = quote,
                            canReorder = settings.sortMode == SortMode.CUSTOM,
                            onClick = { onOpen(item.symbol) },
                            onMove = { delta -> vm.moveSymbol(item.symbol, delta) },
                            onRemove = { vm.removeSymbol(item.symbol) },
                        )
                    }
                    item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AddStockDialog(
            vm = vm,
            onDismiss = { showAdd = false },
            onAdded = { text -> vm.showMessage(text) },
        )
    }
}

private fun sortRows(
    list: List<WatchItem>,
    quotes: Map<String, Quote>,
    mode: SortMode,
): List<Pair<WatchItem, Quote?>> {
    val rows = list.map { it to quotes[it.symbol] }
    return when (mode) {
        SortMode.CUSTOM -> rows
        SortMode.GAIN -> rows.sortedByDescending { it.second?.changePercent ?: Double.NEGATIVE_INFINITY }
        SortMode.LOSS -> rows.sortedBy { it.second?.changePercent ?: Double.POSITIVE_INFINITY }
    }
}

@Composable
private fun Summary(quotes: List<Quote?>) {
    val colors = LocalMarketColors.current
    val up = quotes.count { (it?.change ?: 0.0) > 0 }
    val down = quotes.count { (it?.change ?: 0.0) < 0 }
    val flat = quotes.size - up - down
    val counts = listOf(Triple("上漲", up, colors.up), Triple("平盤", flat, colors.flat), Triple("下跌", down, colors.down))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                counts.forEach { (label, n, color) ->
                    Row(Modifier.alignByBaseline()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$n",
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "共 ${quotes.size} 檔",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            // 漲跌家數比例條
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                counts.filter { it.second > 0 }.forEach { (_, n, color) ->
                    Box(
                        Modifier
                            .weight(n.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

private val GAUGE_WIDTH = 60.dp
private val PRICE_WIDTH = 82.dp
private val PILL_WIDTH = 64.dp

@Composable
private fun ListHeader() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("名稱", style = style, color = color, modifier = Modifier.weight(1f))
        Text("今日 ±10%", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(GAUGE_WIDTH))
        Text("成交價", style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(PRICE_WIDTH))
        Text("漲跌幅", style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(PILL_WIDTH))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StockRow(
    item: WatchItem,
    quote: Quote?,
    canReorder: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalMarketColors.current
    val color = colors.of(quote?.change)
    val limitColor = when {
        quote?.isLimitUp == true -> colors.up
        quote?.isLimitDown == true -> colors.down
        else -> null
    }
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .heightIn(min = 66.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        quote?.name ?: item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (limitColor != null) {
                        Spacer(Modifier.width(6.dp))
                        SolidBadge(if (quote?.isLimitUp == true) "漲停" else "跌停", limitColor)
                    }
                }
                val tag = when {
                    quote == null -> ""
                    quote.isTrial -> " · 試撮"
                    quote.price == null -> " · 尚未成交"
                    else -> ""
                }
                Text(
                    item.symbol + tag,
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DayRangeGauge(
                quote,
                color,
                Modifier
                    .width(GAUGE_WIDTH)
                    .height(20.dp),
            )
            Column(Modifier.width(PRICE_WIDTH), horizontalAlignment = Alignment.End) {
                val priceStyle = MaterialTheme.typography.titleMedium.tabular()
                val price = formatPrice(quote?.price ?: quote?.reference)
                if (limitColor != null) {
                    Text(
                        price,
                        style = priceStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = onColorFor(limitColor),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(limitColor)
                            .padding(horizontal = 4.dp),
                    )
                } else {
                    Text(
                        price,
                        style = priceStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = if (quote?.price == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                }
                Text(
                    formatChangeArrow(quote?.change),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = color,
                )
            }
            Box(Modifier.width(PILL_WIDTH), contentAlignment = Alignment.CenterEnd) {
                Text(
                    formatPercent(quote?.changePercent),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.16f))
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("移到最上面") },
                enabled = canReorder,
                onClick = { onMove(-10_000); menu = false },
            )
            DropdownMenuItem(
                text = { Text("上移") },
                enabled = canReorder,
                onClick = { onMove(-1); menu = false },
            )
            DropdownMenuItem(
                text = { Text("下移") },
                enabled = canReorder,
                onClick = { onMove(1); menu = false },
            )
            DropdownMenuItem(
                text = { Text("刪除") },
                leadingIcon = { Icon(AppIcons.Trash, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { onRemove(); menu = false },
            )
            if (!canReorder) {
                Text(
                    "切回「自訂順序」才能調整位置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * 今日走勢小圖：以參考價為中心、左右各 10%（漲跌停）。
 * 細線是最低到最高，粗塊是開盤到現價，像一根橫放的 K 棒。
 */
@Composable
private fun DayRangeGauge(quote: Quote?, color: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val center = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier) {
        val w = size.width
        val cy = size.height / 2
        drawLine(track, Offset(0f, cy), Offset(w, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(center, Offset(w / 2, cy - 7.dp.toPx()), Offset(w / 2, cy + 7.dp.toPx()), strokeWidth = 1.dp.toPx())

        val q = quote ?: return@Canvas
        val ref = q.reference ?: return@Canvas
        val price = q.price ?: return@Canvas
        if (ref <= 0) return@Canvas
        val open = q.open ?: price
        fun x(v: Double) = (((v / ref - 1) / 0.2 + 0.5).coerceIn(0.0, 1.0) * w).toFloat()

        val lo = x(q.low ?: min(open, price))
        val hi = x(q.high ?: max(open, price))
        drawLine(color, Offset(lo, cy), Offset(max(hi, lo + 1f), cy), strokeWidth = 2.dp.toPx())
        val left = x(min(open, price))
        val right = max(x(max(open, price)), left + 2.dp.toPx())
        drawRoundRect(
            color,
            topLeft = Offset(left, cy - 4.dp.toPx()),
            size = Size(right - left, 8.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(action, onClick = onAction)
    }
}

@Composable
private fun AddStockDialog(vm: AppViewModel, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || text.isBlank()) return
        scope.launch {
            busy = true
            val result = vm.addSymbols(text)
            busy = false
            if (result.added.isNotEmpty()) {
                onAdded("已新增 " + result.added.joinToString("、") { "${it.name}(${it.symbol})" })
            }
            if (result.failed.isEmpty() && result.message == null) {
                onDismiss()
            } else {
                text = result.failed.joinToString(" ")
                error = result.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("新增股票", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = "代號或名稱",
                    placeholder = "2330 0050 鴻海",
                    isError = error != null,
                    errorText = error,
                    singleLine = false,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error == null) {
                    Text(
                        "可一次輸入多檔，用空白或逗號分隔",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton("新增", onClick = { submit() }, enabled = text.isNotBlank(), loading = busy)
        },
        dismissButton = {
            AppTextButton("取消", onClick = onDismiss, enabled = !busy)
        },
    )
}
