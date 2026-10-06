package tw.stockpeek.data

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

/** 解析「5,10,20,60」這種均線設定；最多 4 條，每條 2–240。 */
fun parseMaPeriods(text: String): List<Int> =
    text.split(Regex("[\\s,，、]+"))
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 2..240 }
        .distinct()
        .take(4)
