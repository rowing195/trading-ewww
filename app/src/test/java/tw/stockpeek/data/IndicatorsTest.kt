package tw.stockpeek.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class IndicatorsTest {

    private fun bar(close: Double, high: Double = close, low: Double = close) =
        Candle(time = "", open = close, high = high, low = low, close = close, volume = 0.0)

    @Test
    fun emaWeightsLatestValue() {
        assertArrayEquals(doubleArrayOf(0.0, 2.0), ema(listOf(0.0, 3.0), 2), 1e-9)
        assertArrayEquals(doubleArrayOf(5.0, 5.0, 5.0), ema(listOf(5.0, 5.0, 5.0), 12), 1e-9)
    }

    @Test
    fun rsiUsesWilderSmoothing() {
        val out = rsi(listOf(1.0, 2.0, 3.0, 2.0), 2)
        assertTrue(out[0].isNaN())
        assertTrue(out[1].isNaN())
        assertEquals(100.0, out[2], 1e-9)
        assertEquals(50.0, out[3], 1e-9)
    }

    @Test
    fun rsiAllLossesIsZero() {
        val out = rsi(listOf(10.0, 9.0, 8.0, 7.0), 3)
        assertEquals(0.0, out[3], 1e-9)
    }

    @Test
    fun kdStartsAtFiftyAndSmoothsByThirds() {
        val flat = kd(List(5) { bar(10.0) })
        flat.k.forEach { assertEquals(50.0, it, 1e-9) }
        flat.d.forEach { assertEquals(50.0, it, 1e-9) }

        // 收在最高點 → RSV 100
        val one = kd(listOf(bar(close = 12.0, high = 12.0, low = 8.0)))
        assertEquals(50.0 * 2 / 3 + 100.0 / 3, one.k[0], 1e-9)
        assertEquals(50.0 * 2 / 3 + one.k[0] / 3, one.d[0], 1e-9)
    }

    @Test
    fun macdOfFlatSeriesIsZero() {
        val m = macd(List(40) { 100.0 })
        m.dif.forEach { assertEquals(0.0, it, 1e-9) }
        m.signal.forEach { assertEquals(0.0, it, 1e-9) }
        m.osc.forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test
    fun macdIsPositiveInUptrend() {
        val m = macd(List(60) { 100.0 + it })
        assertTrue(m.dif.last() > 0)
    }

    @Test
    fun bollingerUsesPopulationStdDev() {
        val b = bollinger(listOf(1.0, 2.0, 3.0, 4.0, 5.0), period = 5, width = 2.0)
        assertTrue(b.mid[3].isNaN())
        assertEquals(3.0, b.mid[4], 1e-9)
        assertEquals(3.0 + 2 * sqrt(2.0), b.upper[4], 1e-9)
        assertEquals(3.0 - 2 * sqrt(2.0), b.lower[4], 1e-9)
    }
}
