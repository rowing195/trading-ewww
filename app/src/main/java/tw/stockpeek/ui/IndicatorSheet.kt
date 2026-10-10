package tw.stockpeek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.stockpeek.AppViewModel
import tw.stockpeek.data.AppSettings
import tw.stockpeek.data.DEFAULT_MA
import tw.stockpeek.data.SubIndicator
import tw.stockpeek.data.parseMaPeriods
import tw.stockpeek.ui.theme.LocalMaColors

private const val MA_SLOTS = 5

/** K 線頁的指標設定面板。開關立即生效；均線週期在按「完成」或關閉面板時才存。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorSheet(vm: AppViewModel, settings: AppSettings, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val maColors = LocalMaColors.current
    val maTexts = remember(settings.maPeriods) {
        mutableStateListOf(*Array(MA_SLOTS) { settings.maPeriods.getOrNull(it)?.toString().orEmpty() })
    }

    fun saveMa() {
        val text = maTexts.filter { it.isNotBlank() }.joinToString(",")
        if (parseMaPeriods(text) != settings.maPeriods) vm.setMaPeriods(text)
    }

    ModalBottomSheet(
        onDismissRequest = {
            saveMa()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "指標設定",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                AppTextButton("恢復預設", onClick = {
                    vm.resetChartSettings()
                    DEFAULT_MA.forEachIndexed { i, p -> if (i < MA_SLOTS) maTexts[i] = p.toString() }
                })
            }

            SectionTitle("主圖 · 均線", "最多 5 條 · 週期 2–240 · 留空不顯示")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(MA_SLOTS) { i ->
                    MaField(
                        index = i,
                        value = maTexts[i],
                        color = maColors[i % maColors.size],
                        onChange = { maTexts[i] = it.filter(Char::isDigit).take(3) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            SheetSwitch("成交量疊在主圖", null, settings.showVolume) { vm.setShowVolume(it) }
            SheetSwitch("最高／最低價標記", null, settings.showHiLo) { vm.setShowHiLo(it) }
            SheetSwitch("布林通道", "20, 2", settings.showBollinger) { vm.setShowBollinger(it) }

            SectionTitle("副圖分頁", null)
            SubIndicator.entries.forEach { ind ->
                SheetSwitch(ind.label, ind.params, ind in settings.subIndicators) { vm.setSubIndicator(ind, it) }
            }

            SectionTitle("漲跌顏色", null)
            RedUpChoice(redUp = settings.redUp, onChange = { vm.setRedUp(it) }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "完成",
                onClick = {
                    saveMa()
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                large = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.MaField(index: Int, value: String, color: Color, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .alpha(if (value.isBlank()) 0.3f else 1f)
                .size(width = 20.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text("均線 ${index + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            textStyle = MaterialTheme.typography.titleMedium.tabular().copy(
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "均線 ${index + 1} 週期" },
        )
    }
}

/** 面板裡的開關列，下方接分隔線。 */
@Composable
private fun SheetSwitch(label: String, params: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(label, checked = checked, onChange = onChange, params = params)
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
