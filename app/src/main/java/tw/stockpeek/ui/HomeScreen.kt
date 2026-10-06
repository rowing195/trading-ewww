package tw.stockpeek.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
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
import java.time.format.DateTimeFormatter

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
    var showPercent by rememberSaveable { mutableStateOf(true) }
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
                        TextButton(onClick = { sortMenu = true }) { Text(settings.sortMode.label) }
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
                    IconButton(onClick = { vm.refreshQuotes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新整理")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        floatingActionButton = {
            if (settings.hasApiKey) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增股票")
                }
            }
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
                    body = "按右下角 + 輸入代號或名稱，可以一次貼上好幾檔，例如「2330 0050 00878 鴻海」。",
                    action = "新增股票",
                    onAction = { showAdd = true },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "summary") { Summary(rows.map { it.second }) }
                    items(rows, key = { it.first.symbol }) { (item, quote) ->
                        StockRow(
                            item = item,
                            quote = quote,
                            showPercent = showPercent,
                            canReorder = settings.sortMode == SortMode.CUSTOM,
                            onClick = { onOpen(item.symbol) },
                            onTogglePercent = { showPercent = !showPercent },
                            onMove = { delta -> vm.moveSymbol(item.symbol, delta) },
                            onRemove = { vm.removeSymbol(item.symbol) },
                        )
                    }
                    item(key = "spacer") { Spacer(Modifier.height(88.dp)) }
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("上漲 $up", color = colors.up, style = MaterialTheme.typography.labelLarge)
        Text("平盤 $flat", color = colors.flat, style = MaterialTheme.typography.labelLarge)
        Text("下跌 $down", color = colors.down, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(
            "共 ${quotes.size} 檔",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StockRow(
    item: WatchItem,
    quote: Quote?,
    showPercent: Boolean,
    canReorder: Boolean,
    onClick: () -> Unit,
    onTogglePercent: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalMarketColors.current
    val color = colors.of(quote?.change)
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    quote?.name ?: item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = when {
                    quote == null -> ""
                    quote.isLimitUp -> " · 漲停"
                    quote.isLimitDown -> " · 跌停"
                    quote.isTrial -> " · 試撮"
                    quote.price == null -> " · 尚未成交"
                    else -> ""
                }
                Text(
                    item.symbol + tag,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatPrice(quote?.price ?: quote?.reference),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = if (quote?.price == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                onClick = onTogglePercent,
                color = if (quote?.change == null) MaterialTheme.colorScheme.surfaceVariant else color,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.width(88.dp),
            ) {
                Text(
                    if (showPercent) formatPercent(quote?.changePercent) else formatChange(quote?.change),
                    style = MaterialTheme.typography.titleSmall.tabular(),
                    color = if (quote?.change == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp),
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
                text = { Text("刪除", color = MaterialTheme.colorScheme.error) },
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
        Button(onClick = onAction) { Text(action) }
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
        title = { Text("新增股票") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text("代號或名稱") },
                placeholder = { Text("2330 0050 鴻海") },
                supportingText = { Text(error ?: "可一次輸入多檔，用空白或逗號分隔") },
                isError = error != null,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy && text.isNotBlank()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("新增")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
