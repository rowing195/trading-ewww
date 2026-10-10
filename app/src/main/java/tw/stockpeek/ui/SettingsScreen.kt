package tw.stockpeek.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.stockpeek.AppViewModel
import tw.stockpeek.BuildConfig
import tw.stockpeek.KEY_CLEARED
import tw.stockpeek.KEY_OK
import tw.stockpeek.data.BrokerApp
import tw.stockpeek.data.MarketClock
import java.time.format.DateTimeFormatter

private const val FUGLE_KEY_URL = "https://developer.fugle.tw/docs/key/"
private const val FUGLE_URL = "https://developer.fugle.tw/"
private val testedFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var keyInput by rememberSaveable { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var testedAt by remember { mutableStateOf("") }
    var savingKey by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showIndicators by rememberSaveable { mutableStateOf(false) }

    fun saveKey(value: String) {
        scope.launch {
            savingKey = true
            keyStatus = vm.saveApiKey(value)
            testedAt = MarketClock.now().format(testedFmt)
            savingKey = false
            if (keyStatus == KEY_OK) keyInput = ""
        }
    }

    val keyError = keyStatus.takeIf { it != null && it != KEY_OK && it != KEY_CLEARED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("設定", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ---------- 行情資料 ----------
            SettingsGroup("行情資料", footer = "免費方案每分鐘 60 次；清單 30 檔以內，盤中約 30–40 秒更新一次。") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(AppIcons.Key, size = 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("富果行情 API 金鑰", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (settings.hasApiKey) "加密後只存在這支手機" else "免費申請，不需要在券商開戶",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (settings.hasApiKey) {
                            TintPill("已設定", MaterialTheme.colorScheme.primary)
                        } else {
                            TintPill("尚未設定", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (!settings.hasApiKey) KeySteps(onOpenSite = { openUrl(context, FUGLE_KEY_URL) })

                    AppTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            if (keyError != null) keyStatus = null
                        },
                        label = if (settings.hasApiKey) "更換金鑰" else "API 金鑰",
                        placeholder = if (settings.hasApiKey) "貼上新的 API 金鑰" else "貼上 API 金鑰",
                        isError = keyError != null,
                        errorText = keyError,
                        numbers = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        trailing = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) AppIcons.EyeOff else AppIcons.Eye,
                                    contentDescription = if (showKey) "隱藏金鑰" else "顯示金鑰",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )

                    PrimaryButton(
                        text = if (savingKey) "測試中" else "儲存並測試",
                        onClick = {
                            focus.clearFocus()
                            saveKey(keyInput)
                        },
                        enabled = keyInput.isNotBlank() || savingKey,
                        loading = savingKey,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (keyStatus) {
                        KEY_OK -> Banner("金鑰可用 · 最後測試 $testedAt", BannerKind.Success)
                        KEY_CLEARED -> Banner("已清除金鑰，要重新貼上金鑰才抓得到報價", BannerKind.Info)
                    }

                    if (settings.hasApiKey) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppTextButton(
                                "取得免費金鑰",
                                onClick = { openUrl(context, FUGLE_KEY_URL) },
                                trailingIcon = AppIcons.ArrowOutward,
                            )
                            if (!confirmClear) {
                                DestructiveButton("清除金鑰", onClick = { confirmClear = true }, enabled = !savingKey)
                            }
                        }
                        if (confirmClear) {
                            ConfirmClear(
                                onCancel = { confirmClear = false },
                                onConfirm = {
                                    confirmClear = false
                                    saveKey("")
                                },
                            )
                        }
                    }
                }
            }

            // ---------- 券商捷徑 ----------
            SettingsGroup("券商捷徑", footer = "在 K 線頁點券商時會先複製股票代號，切過去在搜尋框貼上即可。") {
                settings.brokers.forEachIndexed { index, app ->
                    if (index > 0) RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName, 36.dp)
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.moveBroker(app.packageName, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移 ${app.label}")
                        }
                        IconButton(onClick = { vm.removeBroker(app.packageName) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "移除 ${app.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (settings.brokers.isEmpty()) {
                    Text(
                        "還沒有捷徑。加了之後，首頁和 K 線頁底部會出現「下單」按鈕，一鍵切到券商 App。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
                SecondaryButton(
                    "從手機上的 App 加入",
                    onClick = { showPicker = true },
                    icon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            // ---------- K 線 ----------
            SettingsGroup("K 線") {
                SettingsRow(
                    "均線",
                    icon = AppIcons.LineChart,
                    value = settings.maPeriods.joinToString(" · ").ifEmpty { "不顯示" },
                    onClick = { showIndicators = true },
                )
                RowDivider()
                SettingsRow(
                    "副圖指標",
                    icon = AppIcons.SubChart,
                    value = settings.subIndicators.joinToString(" · ") { it.label }.ifEmpty { "不顯示" },
                    onClick = { showIndicators = true },
                )
                RowDivider()
                SwitchRow(
                    "成交量",
                    checked = settings.showVolume,
                    onChange = { vm.setShowVolume(it) },
                    supporting = "疊在主圖下方",
                    contentPadding = PaddingValues(horizontal = 16.dp),
                )
                RowDivider()
                SwitchRow(
                    "最高／最低價標記",
                    checked = settings.showHiLo,
                    onChange = { vm.setShowHiLo(it) },
                    supporting = "標出畫面內的高低點",
                    contentPadding = PaddingValues(horizontal = 16.dp),
                )
                RowDivider()
                SwitchRow(
                    "布林通道",
                    checked = settings.showBollinger,
                    onChange = { vm.setShowBollinger(it) },
                    supporting = "20 日、2 倍標準差",
                    contentPadding = PaddingValues(horizontal = 16.dp),
                )
                RowDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("漲跌顏色", style = MaterialTheme.typography.bodyLarge)
                    RedUpChoice(
                        redUp = settings.redUp,
                        onChange = { vm.setRedUp(it) },
                        modifier = Modifier.fillMaxWidth(),
                        showNotes = true,
                    )
                }
            }

            // ---------- 關於 ----------
            SettingsGroup("關於") {
                SettingsRow("版本", value = BuildConfig.VERSION_NAME, trailing = null)
                RowDivider()
                SettingsRow(
                    "資料來源",
                    value = "富果行情 API",
                    numericValue = false,
                    trailing = AppIcons.ArrowOutward,
                    onClick = { openUrl(context, FUGLE_URL) },
                )
                RowDivider()
                Text(
                    "原始資料為臺灣證券交易所、證券櫃檯買賣中心。股票清單、券商捷徑與設定只存在這支手機，" +
                        "不連任何券商帳號，也不記錄股數或成本。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                )
            }
        }
    }

    if (showPicker) {
        AppPickerSheet(
            existing = settings.brokers.map { it.packageName }.toSet(),
            onToggle = { app, add -> if (add) vm.addBroker(app) else vm.removeBroker(app.packageName) },
            onDismiss = { showPicker = false },
        )
    }
    if (showIndicators) {
        IndicatorSheet(vm = vm, settings = settings, onDismiss = { showIndicators = false })
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

/** 還沒設定金鑰時的三步驟說明。 */
@Composable
private fun KeySteps(onOpenSite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Step(1) {
            Column {
                Text("到富果開發者網站，用富果會員登入", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "開啟網站 ↗",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "開啟富果開發者網站", onClick = onOpenSite)
                        .heightIn(min = 36.dp)
                        .padding(vertical = 8.dp),
                )
            }
        }
        Step(2) { Text("申請免費的行情 API 金鑰", style = MaterialTheme.typography.bodyMedium) }
        Step(3) { Text("複製金鑰，貼到下面再按「儲存並測試」", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Step(number: Int, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium.tabular(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 清除金鑰前的確認。 */
@Composable
private fun ConfirmClear(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("清除後就抓不到報價，要重新貼上金鑰才能使用。確定清除？", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextButton("取消", onClick = onCancel)
            DestructiveButton("清除金鑰", onClick = onConfirm)
        }
    }
}

/** 從手機已安裝的 App 挑券商：可搜尋，點一下加入、再點一下移除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    existing: Set<String>,
    onToggle: (BrokerApp, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val apps by produceState<List<BrokerApp>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    var query by remember { mutableStateOf("") }
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("選擇券商 App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "可以選好幾個；名稱像券商的排在前面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = close) {
                    Icon(Icons.Default.Close, contentDescription = "關閉", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜尋 App 名稱",
                placeholder = "搜尋 App 名稱",
                showLabel = false,
                leading = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            )
            LazyColumn(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                val list = apps
                if (list == null) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                } else {
                    val q = query.trim()
                    val filtered = if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) }
                    val (brokers, others) = filtered.partition { looksLikeBroker(it.label) }
                    listOf("像是券商" to brokers, "其他 App" to others).forEach { (title, group) ->
                        if (group.isEmpty()) return@forEach
                        item(key = "header-$title") {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                            )
                        }
                        items(group, key = { it.packageName }) { app ->
                            PickerRow(app, added = app.packageName in existing, onToggle = onToggle)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "找不到符合「$q」的 App",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已加入 ${existing.size} 個",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("完成", onClick = close, large = true, modifier = Modifier.widthIn(min = 160.dp))
            }
        }
    }
}

@Composable
private fun PickerRow(app: BrokerApp, added: Boolean, onToggle: (BrokerApp, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = added, role = Role.Checkbox, onValueChange = { onToggle(app, it) })
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app.packageName, 40.dp)
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (added) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                Text("已加入", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = scheme.primary)
            }
        } else {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceContainerHigh)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp))
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("加入", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
