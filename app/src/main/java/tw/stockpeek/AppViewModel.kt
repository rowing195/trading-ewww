package tw.stockpeek

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tw.stockpeek.data.ApiException
import tw.stockpeek.data.AppSettings
import tw.stockpeek.data.BrokerApp
import tw.stockpeek.data.Candle
import tw.stockpeek.data.FugleClient
import tw.stockpeek.data.MarketClock
import tw.stockpeek.data.Quote
import tw.stockpeek.data.SYMBOL_REGEX
import tw.stockpeek.data.SettingsStore
import tw.stockpeek.data.SortMode
import tw.stockpeek.data.SubIndicator
import tw.stockpeek.data.Timeframe
import tw.stockpeek.data.WatchItem
import tw.stockpeek.data.parseMaPeriods
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.max

data class AddResult(val added: List<WatchItem>, val failed: List<String>, val message: String?)

/** saveApiKey 測試成功時回傳的訊息。 */
const val KEY_OK = "金鑰可用"

/** saveApiKey 清除金鑰時回傳的訊息。 */
const val KEY_CLEARED = "已清除金鑰"

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val client = FugleClient { store.apiKey() }

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastUpdated = MutableStateFlow<ZonedDateTime?>(null)
    val lastUpdated: StateFlow<ZonedDateTime?> = _lastUpdated.asStateFlow()

    /** 每次報價更新成功 +1，K 線頁的 5 分 K 用它來跟著刷新。 */
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var lastError: String? = null

    /** 訊息顯示完才清掉；期間若有新訊息就不動它。 */
    fun consumeMessage(text: String) {
        _message.compareAndSet(text, null)
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    private val refreshMutex = Mutex()
    private val requestSlots = Semaphore(4)

    // ---------- 報價 ----------

    fun refreshQuotes() {
        viewModelScope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        if (!refreshMutex.tryLock()) return
        try {
            val s = store.current()
            if (!s.hasApiKey || s.watchlist.isEmpty()) return
            _refreshing.value = true
            val results = fetchQuotes(s.watchlist.map { it.symbol })

            val ok = results.mapNotNull { (sym, r) -> r.getOrNull()?.let { sym to it } }
            if (ok.isNotEmpty()) {
                _quotes.update { it + ok }
                _lastUpdated.value = MarketClock.now()
                _refreshTick.update { it + 1 }
                val names = ok.associate { (sym, q) -> sym to q.name }
                if (s.watchlist.any { w -> names[w.symbol]?.let { it != w.name } == true }) {
                    store.updateWatchlist { list ->
                        list.map { w -> names[w.symbol]?.let { w.copy(name = it) } ?: w }
                    }
                }
            }

            val error = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }?.let(::friendly)
            if (error != null && error != lastError) _message.value = error
            lastError = error
        } finally {
            _refreshing.value = false
            refreshMutex.unlock()
        }
    }

    /** 盤中自動更新間隔：清單越長間隔越久，免費方案每分鐘 60 次，留額度給 K 線。 */
    fun autoRefreshDelayMs(): Long {
        val n = settings.value.watchlist.size.coerceAtLeast(1)
        val seconds = max(20, ceil(n * 60.0 / 45.0).toInt())
        return seconds * 1000L
    }

    private suspend fun fetchQuotes(symbols: List<String>): List<Pair<String, Result<Quote>>> =
        coroutineScope {
            symbols.map { sym ->
                async { sym to requestSlots.withPermit { attempt { client.quote(sym) } } }
            }.awaitAll()
        }

    // ---------- 清單 ----------

    private var tickerCache: List<WatchItem>? = null

    private suspend fun allTickers(): List<WatchItem> {
        tickerCache?.let { return it }
        val list = coroutineScope {
            listOf("TWSE", "TPEx").map { ex -> async { client.tickers(ex) } }.awaitAll().flatten()
        }
        tickerCache = list
        return list
    }

    /** 用名稱找代號：完全相符優先，否則回傳所有包含該字串的候選。 */
    private suspend fun lookupByName(name: String): List<WatchItem> {
        val all = allTickers()
        all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return listOf(it) }
        return all.filter { it.name.contains(name, ignoreCase = true) }
    }

    suspend fun addSymbols(raw: String): AddResult {
        val tokens = raw.split(Regex("[\\s,，、;；]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (tokens.isEmpty()) return AddResult(emptyList(), emptyList(), null)

        val existing = store.current().watchlist.map { it.symbol }.toSet()
        val symbols = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val hints = mutableListOf<String>()
        var networkError: String? = null

        for (token in tokens) {
            val upper = token.uppercase()
            if (SYMBOL_REGEX.matches(upper)) {
                symbols += upper
                continue
            }
            val found = attempt { lookupByName(token) }
            found.exceptionOrNull()?.let { networkError = friendly(it) }
            val candidates = found.getOrNull().orEmpty()
            when (candidates.size) {
                1 -> symbols += candidates[0].symbol
                0 -> failed += token
                else -> {
                    failed += token
                    hints += "「$token」有多筆：" +
                        candidates.take(4).joinToString("、") { "${it.name} ${it.symbol}" } +
                        if (candidates.size > 4) "…" else ""
                }
            }
        }

        val results = fetchQuotes(symbols.distinct().filterNot { it in existing })
        val added = results.mapNotNull { (sym, r) -> r.getOrNull()?.let { WatchItem(sym, it.name) } }
        results.forEach { (sym, r) ->
            val e = r.exceptionOrNull() ?: return@forEach
            failed += sym
            if (!(e is ApiException && e.code == 404)) networkError = friendly(e)
        }

        if (added.isNotEmpty()) {
            store.updateWatchlist { cur -> cur + added.filter { a -> cur.none { it.symbol == a.symbol } } }
            _quotes.update { m -> m + results.mapNotNull { (sym, r) -> r.getOrNull()?.let { sym to it } } }
            _lastUpdated.value = MarketClock.now()
        }

        val message = when {
            networkError != null -> networkError
            hints.isNotEmpty() -> hints.joinToString("\n") + "\n請改輸入代號"
            failed.isNotEmpty() -> "找不到：${failed.joinToString("、")}"
            else -> null
        }
        return AddResult(added, failed, message)
    }

    fun removeSymbol(symbol: String) {
        viewModelScope.launch {
            store.updateWatchlist { list -> list.filterNot { it.symbol == symbol } }
            _quotes.update { it - symbol }
        }
    }

    fun moveSymbol(symbol: String, delta: Int) {
        viewModelScope.launch {
            store.updateWatchlist { list ->
                val from = list.indexOfFirst { it.symbol == symbol }
                val to = (from + delta).coerceIn(0, list.lastIndex.coerceAtLeast(0))
                if (from < 0 || from == to) list else list.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { store.setSortMode(mode) }
    }

    // ---------- 設定 ----------

    suspend fun saveApiKey(key: String): String {
        store.setApiKey(key)
        if (key.isBlank()) {
            _quotes.value = emptyMap()
            return KEY_CLEARED
        }
        return attempt { client.quote("2330") }.fold(
            onSuccess = {
                lastError = null
                refreshQuotes()
                KEY_OK
            },
            onFailure = {
                if (it is ApiException && it.code in setOf(401, 403)) {
                    "這把金鑰無法使用（HTTP ${it.code}）。請確認有完整複製，前後沒有多餘的空白。"
                } else {
                    friendly(it)
                }
            },
        )
    }

    fun addBroker(app: BrokerApp) {
        viewModelScope.launch {
            store.updateBrokers { list -> if (list.any { it.packageName == app.packageName }) list else list + app }
        }
    }

    fun removeBroker(packageName: String) {
        viewModelScope.launch { store.updateBrokers { list -> list.filterNot { it.packageName == packageName } } }
    }

    fun moveBroker(packageName: String, delta: Int) {
        viewModelScope.launch {
            store.updateBrokers { list ->
                val from = list.indexOfFirst { it.packageName == packageName }
                val to = (from + delta).coerceIn(0, list.lastIndex.coerceAtLeast(0))
                if (from < 0 || from == to) list else list.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }

    /** 回傳實際套用的均線週期。 */
    fun setMaPeriods(text: String): List<Int> {
        val periods = parseMaPeriods(text)
        viewModelScope.launch { store.setMaPeriods(periods) }
        return periods
    }

    fun setShowVolume(show: Boolean) {
        viewModelScope.launch { store.setShowVolume(show) }
    }

    fun setShowHiLo(show: Boolean) {
        viewModelScope.launch { store.setShowHiLo(show) }
    }

    fun setShowBollinger(show: Boolean) {
        viewModelScope.launch { store.setShowBollinger(show) }
    }

    fun setSubIndicator(indicator: SubIndicator, on: Boolean) {
        val current = settings.value.subIndicators
        val next = SubIndicator.entries.filter { if (it == indicator) on else it in current }
        viewModelScope.launch { store.setSubIndicators(next) }
    }

    fun setRedUp(redUp: Boolean) {
        viewModelScope.launch { store.setRedUp(redUp) }
    }

    fun resetChartSettings() {
        viewModelScope.launch { store.resetChartSettings() }
    }

    // ---------- K 線 ----------

    private val candleCache = HashMap<String, Pair<Long, List<Candle>>>()

    suspend fun candles(symbol: String, tf: Timeframe, force: Boolean): List<Candle> {
        val key = "$symbol|${tf.name}"
        val now = System.currentTimeMillis()
        if (!force && tf != Timeframe.MIN5) {
            candleCache[key]?.let { (time, data) -> if (now - time < 5 * 60_000) return data }
        }
        val today = MarketClock.now().toLocalDate()
        val data = when (tf) {
            Timeframe.MIN5 -> {
                // 前幾天的分 K 走歷史端點；今天盤中的分 K 要從日內端點拿
                val history = client.historicalCandles(symbol, tf.api, today.minusDays(6), today)
                val intraday = attempt { client.intradayCandles(symbol, tf.api) }.getOrDefault(emptyList())
                dedupe(history + intraday)
            }
            // 歷史端點單次查詢要小於 1 年，所以分段抓；週、月 K 的切點對齊週一、月初，避免切出半根 K 棒
            Timeframe.DAY -> chunked(symbol, tf, today, chunks = 2) { it.minusDays(364) }
            Timeframe.WEEK -> chunked(symbol, tf, today, chunks = 5) { // 約 5 年，MA200 才畫得出來
                it.minusDays(350).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            Timeframe.MONTH -> chunked(symbol, tf, today, chunks = 10) {
                it.minusMonths(11).withDayOfMonth(1)
            }
        }
        candleCache[key] = now to data
        return data
    }

    private suspend fun chunked(
        symbol: String,
        tf: Timeframe,
        today: LocalDate,
        chunks: Int,
        fromOf: (LocalDate) -> LocalDate,
    ): List<Candle> {
        val all = ArrayList<Candle>()
        var to = today
        for (k in 0 until chunks) {
            val from = fromOf(to)
            val part = client.historicalCandles(symbol, tf.api, from, to)
            if (part.isEmpty() && k > 0) break // 更早已經沒資料（上市前）
            all += part
            to = from.minusDays(1)
        }
        return dedupe(all)
    }

    private fun dedupe(list: List<Candle>): List<Candle> =
        list.associateBy { it.time.take(16) }.values.sortedBy { it.time }

    // ---------- 共用 ----------

    fun friendly(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "API 錯誤"
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> "網路連線失敗"
        is IOException -> "網路錯誤：${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
