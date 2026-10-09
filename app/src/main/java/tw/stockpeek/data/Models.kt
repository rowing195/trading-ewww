package tw.stockpeek.data

/** 首頁清單上的一檔股票（只存代號與名稱，不存股數或成本）。 */
data class WatchItem(val symbol: String, val name: String)

/** 券商 App 捷徑。 */
data class BrokerApp(val packageName: String, val label: String)

/** 即時報價（富果 /intraday/quote）。 */
data class Quote(
    val symbol: String,
    val name: String,
    val date: String,          // yyyy-MM-dd
    val price: Double?,        // 最新成交價（含試撮）
    val reference: Double?,    // 今日參考價
    val change: Double?,
    val changePercent: Double?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volumeLots: Long?,     // 累計成交量（張）
    val isLimitUp: Boolean,
    val isLimitDown: Boolean,
    val isTrial: Boolean,
)

/** 一根 K 棒。volume 統一換算成「張」。 */
data class Candle(
    val time: String,          // 日／週／月 K：yyyy-MM-dd；分 K：ISO 8601
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

enum class Timeframe(val label: String, val api: String) {
    MIN5("5分", "5"),
    DAY("日K", "D"),
    WEEK("週K", "W"),
    MONTH("月K", "M"),
}

enum class SortMode(val label: String) {
    CUSTOM("自訂順序"),
    GAIN("漲幅大→小"),
    LOSS("跌幅大→小"),
}

/** K 線頁的副圖指標。 */
enum class SubIndicator(val label: String, val params: String) {
    KD("KD", "9, 3, 3"),
    MACD("MACD", "12, 26, 9"),
    RSI("RSI", "6, 12"),
}

data class AppSettings(
    val loaded: Boolean = false,
    val hasApiKey: Boolean = false,
    val watchlist: List<WatchItem> = emptyList(),
    val brokers: List<BrokerApp> = emptyList(),
    val maPeriods: List<Int> = DEFAULT_MA,
    val showVolume: Boolean = true,
    val showHiLo: Boolean = true,
    val showBollinger: Boolean = false,
    val subIndicators: List<SubIndicator> = SubIndicator.entries,
    val redUp: Boolean = true,
    val sortMode: SortMode = SortMode.CUSTOM,
)

val DEFAULT_MA = listOf(5, 10, 20, 60, 200)

/** 台股代號：4–6 碼英數，例如 2330、00878、00631L。 */
val SYMBOL_REGEX = Regex("^[0-9A-Z]{4,6}$")
