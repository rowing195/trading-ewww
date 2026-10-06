package tw.stockpeek.data

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** 台股交易時段判斷（不含國定假日；假日時報價會停在前一個交易日）。 */
object MarketClock {
    val zone: ZoneId = ZoneId.of("Asia/Taipei")

    fun now(): ZonedDateTime = ZonedDateTime.now(zone)

    private fun minutes(t: ZonedDateTime) = t.hour * 60 + t.minute

    private fun isWeekend(t: ZonedDateTime) =
        t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY

    /** 08:30 試撮到 13:35 收盤後幾分鐘，這段時間才自動更新。 */
    fun isActive(t: ZonedDateTime = now()): Boolean =
        !isWeekend(t) && minutes(t) in (8 * 60 + 30)..(13 * 60 + 35)

    fun statusLabel(t: ZonedDateTime = now()): String {
        if (isWeekend(t)) return "休市"
        val m = minutes(t)
        return when {
            m < 8 * 60 + 30 -> "盤前"
            m < 9 * 60 -> "試撮"
            m <= 13 * 60 + 30 -> "盤中"
            else -> "已收盤"
        }
    }
}
