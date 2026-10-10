package tw.stockpeek.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tw.stockpeek.R

/** 漲跌配色；台股慣例漲紅跌綠，設定裡可以反過來。caution 用在過熱、超跌這類提醒。 */
@Immutable
data class MarketColors(val up: Color, val down: Color, val flat: Color, val caution: Color) {
    fun of(change: Double?): Color = when {
        change == null || change == 0.0 -> flat
        change > 0 -> up
        else -> down
    }
}

val LocalMarketColors = staticCompositionLocalOf {
    MarketColors(up = Color(0xFFD93025), down = Color(0xFF138A45), flat = Color(0xFF5F6368), caution = Color(0xFFB45309))
}

/** K 線均線顏色，依序對應設定裡的第 1–5 條；深色主題的第 4 條調亮，文字才看得清楚。 */
private val LightMaColors = listOf(
    Color(0xFFF59E0B),
    Color(0xFF3B82F6),
    Color(0xFFA855F7),
    Color(0xFF64748B),
    Color(0xFF0891B2),
)
private val DarkMaColors = LightMaColors.toMutableList().apply { this[3] = Color(0xFF94A3B8) }

val LocalMaColors = staticCompositionLocalOf { LightMaColors }

/** 價格、漲跌、指標數值用的等寬數字字型（IBM Plex Mono，內嵌在 App 裡）。 */
val NumberFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** 圓角：8 標籤、12 按鈕與輸入框、16 卡片、22 底部面板與對話框。 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/** 色塊上的文字顏色：挑對比較高的深色或白色。 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.19f) Color(0xFF0E1015) else Color.White

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
    surfaceContainerHighest = Color(0xFFE1E4EA),
    outline = Color(0xFFA9AFBA),
    outlineVariant = Color(0xFFD5D9E1),
    // 紅綠留給漲跌，錯誤一律用琥珀色
    error = Color(0xFFB45309),
    onError = Color.White,
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
    surfaceContainerHighest = Color(0xFF262A33),
    outline = Color(0xFF4A5060),
    outlineVariant = Color(0xFF2C313C),
    error = Color(0xFFF59E0B),
    onError = Color(0xFF0E1015),
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
        caution = if (dark) Color(0xFFF59E0B) else Color(0xFFB45309),
    )
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, shapes = AppShapes) {
        CompositionLocalProvider(
            LocalMarketColors provides market,
            LocalMaColors provides if (dark) DarkMaColors else LightMaColors,
            content = content,
        )
    }
}
