package tw.stockpeek.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.stockpeek.ui.theme.LocalMarketColors

// 「夜盤」風格的共用元件：按鈕、開關、輸入框、設定列、提示列。
// 規則：紅綠只表示漲跌；成功用重點藍、錯誤用琥珀；刪除類用中性外框並先確認。

private val ButtonShape = RoundedCornerShape(12.dp)

/** 主要按鈕，一個畫面最多一顆。large 用在面板底部的「完成」。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    large: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(if (large) 14.dp else 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.primary.copy(alpha = 0.38f),
            disabledContentColor = scheme.onPrimary.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.heightIn(min = if (large) 52.dp else 48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = scheme.onPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = if (large) 16.sp else 15.sp),
            fontWeight = if (large) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** 次要按鈕：淡底加細框，同一區塊裡的一般動作。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
    }
}

/** 文字按鈕：外部連結、恢復預設、取消這類輔助動作。 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** 刪除類按鈕：不用紅色（紅色代表上漲），中性外框加垃圾桶；按下後應先確認。 */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
        border = BorderStroke(1.dp, scheme.outline),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Icon(AppIcons.Trash, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium)
    }
}

/** 開關：開是重點藍，關是軌道灰，沒有外框。 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedTrackColor = scheme.outlineVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** 整列可點的開關列；params 是右側的參數說明（例如「20, 2」）。 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    params: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(contentPadding)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (params != null) {
            Text(
                params,
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
        }
        AppSwitch(checked = checked, onCheckedChange = null)
    }
}

/** 漲跌顏色的兩張選項卡。 */
@Composable
fun RedUpChoice(redUp: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, showNotes: Boolean = false) {
    val market = LocalMarketColors.current
    val red = if (redUp) market.up else market.down
    val green = if (redUp) market.down else market.up
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(true, "紅漲綠跌", "台股慣例"),
            Triple(false, "綠漲紅跌", "美股慣例"),
        ).forEach { (value, label, note) ->
            val selected = value == redUp
            val scheme = MaterialTheme.colorScheme
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = if (showNotes) 56.dp else 46.dp)
                    .clip(shape)
                    .background(if (selected) scheme.outlineVariant else scheme.surfaceContainerHigh)
                    .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(value) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▲", style = MaterialTheme.typography.labelSmall, color = if (value) red else green)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text("▼", style = MaterialTheme.typography.labelSmall, color = if (value) green else red)
                }
                if (showNotes) {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

enum class BannerKind { Success, Warning, Info }

/** 提示列：成功用重點藍、警告與錯誤用琥珀、一般資訊用中性色，都附圖示。 */
@Composable
fun Banner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, icon) = when (kind) {
        BannerKind.Success -> Triple(scheme.primary.copy(alpha = 0.12f), scheme.primary, AppIcons.Check)
        BannerKind.Warning -> Triple(scheme.error.copy(alpha = 0.12f), scheme.error, AppIcons.Alert)
        BannerKind.Info -> Triple(scheme.surfaceContainerHigh, scheme.onSurface, AppIcons.Info)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

/** 設定區塊：小標題＋圓角卡片＋卡片下方的說明。 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** 設定卡片裡的分隔線。 */
@Composable
fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** 設定列左側的圖示方塊。 */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size >= 40.dp) 12.dp else 10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** 設定列：圖示＋標題（＋說明）＋右側值＋箭頭；有 onClick 時整列可點。 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    value: String? = null,
    numericValue: Boolean = true,
    trailing: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    onClick: (() -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        if (value != null) {
            val style = MaterialTheme.typography.labelLarge
            Text(value, style = if (numericValue) style.tabular() else style, color = muted, maxLines = 1)
        }
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** 輸入框：標籤在框外上方，聚焦時重點藍框，錯誤時琥珀框加說明。 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    errorText: String? = null,
    numbers: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    showLabel: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> scheme.outlineVariant
    }
    val shape = RoundedCornerShape(12.dp)
    val baseStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = scheme.onSurface)
    val textStyle = if (numbers) baseStyle.tabular() else baseStyle
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(scheme.background)
                        .border(if (isError || focused) 2.dp else 1.dp, borderColor, shape)
                        .padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = baseStyle, color = scheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (isError && errorText != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(AppIcons.Alert, contentDescription = null, tint = scheme.error, modifier = Modifier.size(16.dp))
                Text(errorText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = scheme.error)
            }
        }
    }
}
