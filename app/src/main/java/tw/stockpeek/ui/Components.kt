package tw.stockpeek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tw.stockpeek.ui.theme.LocalMarketColors
import tw.stockpeek.ui.theme.onColorFor

/** 分段切換：選中的那格有底色。equalWidth = 每格等寬撐滿整列。 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = true,
    itemHeight: Dp = 38.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainer)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                (if (equalWidth) Modifier.weight(1f) else Modifier)
                    .height(itemHeight)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) scheme.outlineVariant else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 淡底色的狀態標籤，例如「偏多」「站上」。 */
@Composable
fun TintPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Text(
        text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 實心色塊標籤，用在漲停／跌停。 */
@Composable
fun SolidBadge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = onColorFor(color),
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 訊號顏色：偏多用漲色、偏空用跌色、過熱超跌用提醒色，中性用次要文字色。 */
@Composable
fun signalColor(bias: Int, caution: Boolean = false): Color {
    val market = LocalMarketColors.current
    return when {
        caution -> market.caution
        bias > 0 -> market.up
        bias < 0 -> market.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** 綜合判讀 0–4 級的顏色。 */
@Composable
fun levelColor(level: Int): Color = signalColor(bias = level - 2)
