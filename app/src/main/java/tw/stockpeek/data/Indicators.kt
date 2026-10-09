package tw.stockpeek.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 簡單移動平均；資料不足的位置填 NaN。 */
fun movingAverage(values: List<Double>, period: Int): DoubleArray {
    val out = DoubleArray(values.size) { Double.NaN }
    if (period <= 0) return out
    var sum = 0.0
    for (i in values.indices) {
        sum += values[i]
        if (i >= period) sum -= values[i - period]
        if (i >= period - 1) out[i] = sum / period
    }
    return out
}

/** 解析「5,10,20,60,200」這種均線設定；最多 5 條，每條 2–240。 */
fun parseMaPeriods(text: String): List<Int> =
    text.split(Regex("[\\s,，、]+"))
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 2..240 }
        .distinct()
        .take(5)

/** 指數移動平均；從第一筆開始算，不留空。 */
fun ema(values: List<Double>, period: Int): DoubleArray {
    val out = DoubleArray(values.size)
    val k = 2.0 / (period + 1)
    for (i in values.indices) {
        out[i] = if (i == 0) values[0] else values[i] * k + out[i - 1] * (1 - k)
    }
    return out
}

class Kd(val k: DoubleArray, val d: DoubleArray)

/** KD(9,3,3)：RSV 取近 n 根的高低點，K、D 各以 1/3 權重平滑，起始值 50。 */
fun kd(bars: List<Candle>, n: Int = 9): Kd {
    val k = DoubleArray(bars.size)
    val d = DoubleArray(bars.size)
    var pk = 50.0
    var pd = 50.0
    for (i in bars.indices) {
        var hi = Double.NEGATIVE_INFINITY
        var lo = Double.POSITIVE_INFINITY
        for (j in max(0, i - n + 1)..i) {
            hi = max(hi, bars[j].high)
            lo = min(lo, bars[j].low)
        }
        val rsv = if (hi > lo) (bars[i].close - lo) / (hi - lo) * 100 else 50.0
        pk = pk * 2 / 3 + rsv / 3
        pd = pd * 2 / 3 + pk / 3
        k[i] = pk
        d[i] = pd
    }
    return Kd(k, d)
}

class Macd(val dif: DoubleArray, val signal: DoubleArray, val osc: DoubleArray)

/** MACD(12,26,9)：DIF = 快慢 EMA 差，signal = DIF 的 EMA，osc = 柱狀體。 */
fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): Macd {
    val ef = ema(closes, fast)
    val es = ema(closes, slow)
    val dif = DoubleArray(closes.size) { ef[it] - es[it] }
    val sig = ema(dif.toList(), signal)
    return Macd(dif, sig, DoubleArray(closes.size) { dif[it] - sig[it] })
}

/** RSI（Wilder 平滑）；前 period 根資料不足填 NaN。 */
fun rsi(closes: List<Double>, period: Int): DoubleArray {
    val out = DoubleArray(closes.size) { Double.NaN }
    var gain = 0.0
    var loss = 0.0
    for (i in 1 until closes.size) {
        val ch = closes[i] - closes[i - 1]
        val g = max(ch, 0.0)
        val l = max(-ch, 0.0)
        if (i <= period) {
            gain += g / period
            loss += l / period
        } else {
            gain = (gain * (period - 1) + g) / period
            loss = (loss * (period - 1) + l) / period
        }
        if (i >= period) out[i] = if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
    }
    return out
}

class Bollinger(val mid: DoubleArray, val upper: DoubleArray, val lower: DoubleArray)

/** 布林通道：中線為 period 日均線，上下軌各加減 width 倍標準差（母體標準差）。 */
fun bollinger(closes: List<Double>, period: Int = 20, width: Double = 2.0): Bollinger {
    val mid = movingAverage(closes, period)
    val upper = DoubleArray(closes.size) { Double.NaN }
    val lower = DoubleArray(closes.size) { Double.NaN }
    for (i in closes.indices) {
        if (mid[i].isNaN()) continue
        var sq = 0.0
        for (j in i - period + 1..i) sq += (closes[j] - mid[i]).let { it * it }
        val sd = sqrt(sq / period)
        upper[i] = mid[i] + width * sd
        lower[i] = mid[i] - width * sd
    }
    return Bollinger(mid, upper, lower)
}
