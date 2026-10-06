package tw.stockpeek.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 漲跌配色；台股慣例漲紅跌綠，設定裡可以反過來。 */
@Immutable
data class MarketColors(val up: Color, val down: Color, val flat: Color) {
    fun of(change: Double?): Color = when {
        change == null || change == 0.0 -> flat
        change > 0 -> up
        else -> down
    }
}

val LocalMarketColors = staticCompositionLocalOf {
    MarketColors(up = Color(0xFFD93025), down = Color(0xFF138A45), flat = Color(0xFF5F6368))
}

/** K 線均線顏色，依序對應設定裡的第 1–4 條。 */
val MaColors = listOf(
    Color(0xFFF59E0B),
    Color(0xFF3B82F6),
    Color(0xFFA855F7),
    Color(0xFF64748B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B2A7A),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF15181E),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF5B616E),
    surfaceContainer = Color(0xFFEFF1F5),
    surfaceContainerHigh = Color(0xFFE8EBF0),
    outlineVariant = Color(0xFFD5D9E1),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB6FF),
    onPrimary = Color(0xFF0B2A7A),
    primaryContainer = Color(0xFF233C85),
    onPrimaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF232731),
    onSurfaceVariant = Color(0xFF9AA1AE),
    surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF1E222B),
    outlineVariant = Color(0xFF2C313C),
)

@Composable
fun StockPeekTheme(redUp: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val red = if (dark) Color(0xFFFF5A5F) else Color(0xFFD93025)
    val green = if (dark) Color(0xFF2ECC71) else Color(0xFF138A45)
    val market = MarketColors(
        up = if (redUp) red else green,
        down = if (redUp) green else red,
        flat = if (dark) Color(0xFF9AA0A6) else Color(0xFF5F6368),
    )
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        CompositionLocalProvider(LocalMarketColors provides market, content = content)
    }
}
