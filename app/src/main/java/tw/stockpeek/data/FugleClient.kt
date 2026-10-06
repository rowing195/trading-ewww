package tw.stockpeek.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * 富果行情 REST API（https://developer.fugle.tw/docs/data/http-api/getting-started）。
 * 免費方案：日內行情 60 次/分、歷史行情 60 次/分。
 */
class FugleClient(private val apiKey: suspend () -> String?) {

    private val base = "https://api.fugle.tw/marketdata/v1.0/stock"

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: throw ApiException(401, "尚未設定富果 API 金鑰")
            val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val url = URI.create(if (query.isEmpty()) "$base$path" else "$base$path?$query").toURL()
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("X-API-KEY", key)
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw ApiException(code, errorMessage(code, body))
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        401, 403 -> "API 金鑰無效，請到設定重新輸入"
        404 -> "查無資料"
        429 -> "超過每分鐘呼叫上限，稍後會自動再試"
        else -> runCatching { JSONObject(body).optString("message") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "伺服器回應 HTTP $code"
    }

    suspend fun quote(symbol: String): Quote {
        val j = get("/intraday/quote/$symbol")
        val total = j.optJSONObject("total")
        return Quote(
            symbol = j.optString("symbol", symbol),
            name = j.optString("name", symbol),
            date = j.optString("date"),
            price = j.doubleOrNull("lastPrice") ?: j.doubleOrNull("closePrice"),
            reference = j.doubleOrNull("referencePrice") ?: j.doubleOrNull("previousClose"),
            change = j.doubleOrNull("change"),
            changePercent = j.doubleOrNull("changePercent"),
            open = j.doubleOrNull("openPrice"),
            high = j.doubleOrNull("highPrice"),
            low = j.doubleOrNull("lowPrice"),
            volumeLots = total?.doubleOrNull("tradeVolume")?.toLong(),
            isLimitUp = j.optBoolean("isLimitUpPrice"),
            isLimitDown = j.optBoolean("isLimitDownPrice"),
            isTrial = j.optBoolean("isTrial"),
        )
    }

    /** 歷史 K 線。單次區間須小於 1 年；查無資料時富果回 404，這裡轉成空清單。 */
    suspend fun historicalCandles(
        symbol: String,
        timeframe: String,
        from: LocalDate,
        to: LocalDate,
    ): List<Candle> {
        val j = try {
            get(
                "/historical/candles/$symbol",
                mapOf(
                    "timeframe" to timeframe,
                    "from" to from.toString(),
                    "to" to to.toString(),
                    "fields" to "open,high,low,close,volume",
                    "sort" to "asc",
                ),
            )
        } catch (e: ApiException) {
            if (e.code == 404) return emptyList() else throw e
        }
        // 整股：分 K 單位是「張」，日／週／月 K 是「股」
        val isMinute = timeframe.all { it.isDigit() }
        return parseCandles(j, volumeDivisor = if (isMinute) 1.0 else 1000.0)
    }

    /** 今日分 K（盤中即時；歷史分 K 要盤後 16:30 才補齊）。單位：張。 */
    suspend fun intradayCandles(symbol: String, timeframe: String): List<Candle> {
        val j = try {
            get("/intraday/candles/$symbol", mapOf("timeframe" to timeframe, "sort" to "asc"))
        } catch (e: ApiException) {
            if (e.code == 404) return emptyList() else throw e
        }
        return parseCandles(j, volumeDivisor = 1.0)
    }

    /** 上市或上櫃全部股票（含 ETF）的代號與名稱，用來讓你用名稱新增。 */
    suspend fun tickers(exchange: String): List<WatchItem> {
        val j = get("/intraday/tickers", mapOf("type" to "EQUITY", "exchange" to exchange))
        val arr = j.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val s = o.optString("symbol")
            if (s.isBlank()) null else WatchItem(s, o.optString("name", s))
        }
    }

    private fun parseCandles(j: JSONObject, volumeDivisor: Double): List<Candle> {
        val arr = j.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Candle>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val open = c.doubleOrNull("open") ?: continue
            val high = c.doubleOrNull("high") ?: continue
            val low = c.doubleOrNull("low") ?: continue
            val close = c.doubleOrNull("close") ?: continue
            out += Candle(
                time = c.optString("date"),
                open = open,
                high = high,
                low = low,
                close = close,
                volume = (c.doubleOrNull("volume") ?: 0.0) / volumeDivisor,
            )
        }
        return out.sortedBy { it.time }
    }
}

private fun JSONObject.doubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null
