package tw.stockpeek.data

import java.util.Locale
import kotlin.math.abs

/** 一項技術訊號：bias 1 偏多、0 中性、-1 偏空；caution = 過熱或超跌，需要留意。 */
data class Signal(val state: String, val bias: Int, val caution: Boolean = false, val note: String = "")

val LEVEL_LABELS = listOf("強烈偏空", "偏空", "中性", "偏多", "強烈偏多")

/** 至少要有這麼多根日 K 才判讀（季線與 60 日高低點）。 */
const val MIN_SUMMARY_BARS = 60

/**
 * 日 K 技術面摘要：均線排列、月線／季線／200 日線、KD、MACD、RSI、量能共 8 項，
 * 加總後分成 5 級。均線用固定週期，不跟著使用者的均線設定變動。
 */
class TechSummary(
    val close: Double,
    val prevClose: Double,
    val alignment: Signal,
    val trend: List<Signal>,
    val kdSignal: Signal,
    val k: Double,
    val d: Double,
    val macdSignal: Signal,
    val dif: Double,
    val signalLine: Double,
    val osc: Double,
    val oscRecent: List<Double>,
    val rsiSignal: Signal,
    val rsi6: Double,
    val rsi12: Double,
    val volumeSignal: Signal,
    val volume: Double,
    val volumeMa5: Double,
    val volumeMa20: Double,
    val ma20: Double,
    val high20: Double,
    val low20: Double,
    val high60: Double,
    val low60: Double,
) {
    val signals: List<Signal> get() = listOf(alignment) + trend + listOf(kdSignal, macdSignal, rsiSignal, volumeSignal)

    val score: Int get() = signals.sumOf { it.bias }

    /** 0 強烈偏空 … 4 強烈偏多。 */
    val level: Int
        get() = when {
            score >= 5 -> 4
            score >= 2 -> 3
            score >= -1 -> 2
            score >= -4 -> 1
            else -> 0
        }

    val label: String get() = LEVEL_LABELS[level]

    /** K 線頁摘要列顯示的重點。 */
    val highlights: List<String> get() = listOf(alignment.state, "KD ${kdSignal.state}", volumeSignal.state)
}

fun analyzeDaily(bars: List<Candle>): TechSummary? {
    if (bars.size < MIN_SUMMARY_BARS) return null
    val closes = bars.map { it.close }
    val n = bars.lastIndex
    val c = closes[n]
    fun ma(period: Int) = movingAverage(closes, period)[n]

    val ma5 = ma(5)
    val ma10 = ma(10)
    val ma20 = ma(20)
    val ma60 = ma(60)
    val alignment = when {
        ma5 > ma10 && ma10 > ma20 && ma20 > ma60 ->
            Signal("多頭排列", 1, note = "短中期均線由上而下依序排列，趨勢向上。")
        ma5 < ma10 && ma10 < ma20 && ma20 < ma60 ->
            Signal("空頭排列", -1, note = "短中期均線由下而上依序排列，趨勢向下。")
        else -> Signal("均線糾結", 0, note = "均線交錯，趨勢尚未明朗。")
    }
    fun position(name: String, v: Double): Signal {
        val label = if (name.first().isDigit()) " $name" else name // 中文接數字時空一格
        return when {
            v.isNaN() -> Signal("資料不足", 0)
            c >= v -> Signal("站上$label", 1)
            else -> Signal("跌破$label", -1)
        }
    }
    val trend = listOf(position("月線", ma20), position("季線", ma60), position("200 日線", ma(200)))

    val kdv = kd(bars)
    val k = kdv.k[n]
    val d = kdv.d[n]
    val crossedUp = kdv.k[n - 1] <= kdv.d[n - 1] && k > d
    val crossedDown = kdv.k[n - 1] >= kdv.d[n - 1] && k < d
    val kdSignal = when {
        k >= 80 -> Signal("高檔", 0, caution = true, note = "K 值在 80 以上，屬高檔區。強勢股常在高檔鈍化；若 K 向下跌破 D，視為短線轉弱。")
        k <= 20 -> Signal("低檔", 0, caution = true, note = "K 值在 20 以下，屬低檔區。弱勢股可能低檔鈍化；若 K 向上穿越 D，視為短線轉強。")
        crossedUp -> Signal("黃金交叉", 1, note = "K 由下往上穿越 D，短線轉強。")
        crossedDown -> Signal("死亡交叉", -1, note = "K 由上往下跌破 D，短線轉弱。")
        k > d -> Signal("偏多", 1, note = "K 位於 D 之上，短線偏多。")
        else -> Signal("偏空", -1, note = "K 位於 D 之下，短線偏空。")
    }

    val m = macd(closes)
    val osc = m.osc[n]
    val prevOsc = m.osc[n - 1]
    var days = 0
    while (days <= n && (m.osc[n - days] > 0) == (osc > 0)) days++
    val (macdState, macdBias) = when {
        osc > 0 && prevOsc <= 0 -> "翻正" to 1
        osc <= 0 && prevOsc > 0 -> "翻負" to -1
        osc > 0 -> if (osc >= prevOsc) "多方增強" to 1 else "多方減弱" to 0
        else -> if (osc <= prevOsc) "空方增強" to -1 else "空方減弱" to 0
    }
    val macdSignal = Signal(
        macdState,
        macdBias,
        note = "柱狀體連續 $days 天為${if (osc > 0) "正" else "負"}，" +
            (if (abs(osc) >= abs(prevOsc)) "力道放大中" else "力道收斂中") +
            "；DIF 位於零軸${if (m.dif[n] >= 0) "之上" else "之下"}。",
    )

    val rsi6 = rsi(closes, 6)[n]
    val rsi12 = rsi(closes, 12)[n]
    val rsiSignal = when {
        rsi6 >= 80 -> Signal("超買", 0, caution = true, note = "RSI6 高於 80，短線過熱，留意拉回。")
        rsi6 <= 20 -> Signal("超賣", 0, caution = true, note = "RSI6 低於 20，短線超跌，留意反彈。")
        rsi6 >= 50 -> Signal("強勢", 1, note = "RSI6 位於 50 以上，近期買方力道較強。")
        else -> Signal("弱勢", -1, note = "RSI6 位於 50 以下，近期賣方力道較強。")
    }

    // 均量取「今天以前」的 5／20 日，今天的量拿來跟它比
    val volumes = bars.map { it.volume }
    val volumeMa5 = movingAverage(volumes, 5)[n - 1]
    val volumeMa20 = movingAverage(volumes, 20)[n - 1]
    val ratio = if (volumeMa5 > 0) bars[n].volume / volumeMa5 else Double.NaN
    val rising = c >= closes[n - 1]
    val ratioText = String.format(Locale.US, "%.2f", ratio)
    val volumeSignal = when {
        ratio.isNaN() -> Signal("量能不明", 0)
        ratio >= 1.3 -> Signal(
            if (rising) "價漲量增" else "價跌量增",
            if (rising) 1 else -1,
            note = "今日量為 5 日均量的 $ratioText 倍，" + if (rising) "上漲有量能支撐。" else "下跌伴隨出量，賣壓較重。",
        )
        ratio <= 0.7 -> Signal("量縮", 0, note = "今日量為 5 日均量的 $ratioText 倍，交投清淡。")
        else -> Signal("量能持平", 0, note = "今日量為 5 日均量的 $ratioText 倍，與近期相當。")
    }

    val last20 = bars.takeLast(20)
    val last60 = bars.takeLast(60)
    return TechSummary(
        close = c,
        prevClose = closes[n - 1],
        alignment = alignment,
        trend = trend,
        kdSignal = kdSignal,
        k = k,
        d = d,
        macdSignal = macdSignal,
        dif = m.dif[n],
        signalLine = m.signal[n],
        osc = osc,
        oscRecent = m.osc.takeLast(30),
        rsiSignal = rsiSignal,
        rsi6 = rsi6,
        rsi12 = rsi12,
        volumeSignal = volumeSignal,
        volume = bars[n].volume,
        volumeMa5 = volumeMa5,
        volumeMa20 = volumeMa20,
        ma20 = ma20,
        high20 = last20.maxOf { it.high },
        low20 = last20.minOf { it.low },
        high60 = last60.maxOf { it.high },
        low60 = last60.minOf { it.low },
    )
}
