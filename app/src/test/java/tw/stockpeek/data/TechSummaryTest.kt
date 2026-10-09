package tw.stockpeek.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechSummaryTest {

    /** 每天固定漲跌 step，最後一天量是平常的兩倍。 */
    private fun series(count: Int, step: Double): List<Candle> = List(count) { i ->
        val close = 500.0 + step * i
        Candle(
            time = "d$i",
            open = close - step,
            high = close + 0.5,
            low = close - 0.5,
            close = close,
            volume = if (i == count - 1) 2000.0 else 1000.0,
        )
    }

    @Test
    fun notEnoughBarsGivesNoSummary() {
        assertNull(analyzeDaily(series(MIN_SUMMARY_BARS - 1, 1.0)))
    }

    @Test
    fun steadyUptrendIsStronglyBullish() {
        val s = analyzeDaily(series(250, 1.0))!!
        assertEquals("多頭排列", s.alignment.state)
        assertEquals(listOf("站上月線", "站上季線", "站上 200 日線"), s.trend.map { it.state })
        assertEquals("高檔", s.kdSignal.state)
        assertTrue(s.kdSignal.caution)
        assertEquals("超買", s.rsiSignal.state)
        assertEquals("價漲量增", s.volumeSignal.state)
        assertEquals(4, s.level)
        assertEquals("強烈偏多", s.label)
    }

    @Test
    fun steadyDowntrendIsStronglyBearish() {
        val s = analyzeDaily(series(250, -1.0))!!
        assertEquals("空頭排列", s.alignment.state)
        assertEquals("低檔", s.kdSignal.state)
        assertEquals("超賣", s.rsiSignal.state)
        assertEquals("價跌量增", s.volumeSignal.state)
        assertEquals(0, s.level)
    }

    @Test
    fun shortHistorySkips200DayLine() {
        val s = analyzeDaily(series(100, 1.0))!!
        assertEquals("資料不足", s.trend[2].state)
        assertEquals(0, s.trend[2].bias)
    }

    @Test
    fun keyLevelsUseRecentHighsAndLows() {
        val bars = series(100, 1.0)
        val s = analyzeDaily(bars)!!
        assertEquals(bars.last().high, s.high20, 1e-9)
        assertEquals(bars[80].low, s.low20, 1e-9)
        assertEquals(bars[40].low, s.low60, 1e-9)
    }
}
