package tw.stockpeek.ui

import androidx.compose.ui.text.TextStyle
import tw.stockpeek.data.Timeframe
import tw.stockpeek.ui.theme.NumberFamily
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs

/** 等寬數字字型，價格欄位才會對齊；中文字會自動退回系統字型。 */
fun TextStyle.tabular(): TextStyle = copy(fontFamily = NumberFamily, fontFeatureSettings = "tnum")

fun formatPrice(v: Double?): String = v?.let { DecimalFormat("#,##0.##").format(it) } ?: "--"

fun formatChange(v: Double?): String =
    v?.let { if (it == 0.0) "0" else DecimalFormat("+#,##0.##;-#,##0.##").format(it) } ?: "--"

/** 漲跌點數加箭頭：▲19、▼2.5、平盤 0。 */
fun formatChangeArrow(v: Double?): String = when {
    v == null -> "--"
    v > 0 -> "▲" + DecimalFormat("#,##0.##").format(v)
    v < 0 -> "▼" + DecimalFormat("#,##0.##").format(-v)
    else -> "0"
}

fun formatPercent(v: Double?): String =
    v?.let { if (it == 0.0) "0.00%" else String.format(Locale.US, "%+.2f%%", it) } ?: "--"

/** 指標數值：固定小數位，NaN 顯示 --。 */
fun formatFixed(v: Double?, decimals: Int): String =
    if (v == null || v.isNaN()) "--" else String.format(Locale.US, "%.${decimals}f", v)

/** 張數：1 萬張以上用「萬」。 */
fun formatLots(v: Double?): String = when {
    v == null -> "--"
    abs(v) >= 100_000 -> DecimalFormat("#,##0.#").format(v / 10_000) + "萬"
    else -> DecimalFormat("#,##0").format(v)
}

/** K 棒時間 → 顯示用字串。full=true 用在資訊列，false 用在橫軸。 */
fun formatBarTime(time: String, tf: Timeframe, full: Boolean): String {
    if (time.length < 10) return time
    val y = time.substring(0, 4)
    val m = time.substring(5, 7)
    val d = time.substring(8, 10)
    return when (tf) {
        Timeframe.MIN5 -> {
            val hm = if (time.length >= 16) time.substring(11, 16) else ""
            if (full) "$m/$d $hm" else hm
        }
        Timeframe.DAY -> if (full) "$y/$m/$d" else "$m/$d"
        Timeframe.WEEK -> if (full) "$y/$m/$d 當週" else "${y.takeLast(2)}/$m"
        Timeframe.MONTH -> if (full) "$y/$m" else "${y.takeLast(2)}/$m"
    }
}
