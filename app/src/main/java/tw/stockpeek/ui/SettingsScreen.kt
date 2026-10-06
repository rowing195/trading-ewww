package tw.stockpeek.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.stockpeek.AppViewModel
import tw.stockpeek.data.BrokerApp

private const val FUGLE_KEY_URL = "https://developer.fugle.tw/docs/key/"

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
    var savingKey by remember { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var maText by rememberSaveable(settings.maPeriods) { mutableStateOf(settings.maPeriods.joinToString(",")) }

    fun saveKey(value: String) {
        scope.launch {
            savingKey = true
            keyStatus = vm.saveApiKey(value)
            savingKey = false
            if (keyStatus?.endsWith("✓") == true) keyInput = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- 行情金鑰 ----------
            Section("行情資料（富果 API）") {
                Text(
                    if (settings.hasApiKey) "已設定金鑰 ✓（以 Android Keystore 加密存在本機）" else "尚未設定金鑰",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(if (settings.hasApiKey) "換一把新金鑰" else "貼上 API 金鑰") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隱藏" else "顯示") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            focus.clearFocus()
                            saveKey(keyInput)
                        },
                        enabled = keyInput.isNotBlank() && !savingKey,
                    ) {
                        if (savingKey) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("儲存並測試")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, FUGLE_KEY_URL.toUri())) }
                    }) { Text("取得免費金鑰") }
                    if (settings.hasApiKey) {
                        TextButton(onClick = { saveKey("") }, enabled = !savingKey) { Text("清除") }
                    }
                }
                keyStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.endsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Hint("到富果開發者網站登入會員 → 金鑰申請。免費方案每分鐘 60 次，清單 30 檔以內盤中約 30–40 秒更新一次。")
            }

            // ---------- 券商捷徑 ----------
            Section("券商 App 捷徑") {
                if (settings.brokers.isEmpty()) {
                    Hint("還沒有捷徑。加了之後首頁和 K 線頁底部會出現按鈕，一鍵切過去下單。")
                }
                settings.brokers.forEachIndexed { index, app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.moveBroker(app.packageName, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(onClick = { vm.removeBroker(app.packageName) }) {
                            Icon(Icons.Default.Close, contentDescription = "移除")
                        }
                    }
                }
                OutlinedButton(onClick = { showPicker = true }) { Text("＋ 從手機上的 App 選擇") }
                Hint("在 K 線頁點券商時會自動複製股票代號，切過去在搜尋框貼上即可。")
            }

            // ---------- K 線 ----------
            Section("K 線") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = maText,
                        onValueChange = { maText = it },
                        label = { Text("均線週期") },
                        supportingText = { Text("最多 4 條，用逗號分隔；清空＝不畫均線") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        focus.clearFocus()
                        maText = vm.setMaPeriods(maText).joinToString(",")
                    }) { Text("套用") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("顯示成交量", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = settings.showVolume, onCheckedChange = { vm.setShowVolume(it) })
                }
                ColorOption("漲紅跌綠（台股慣例）", selected = settings.redUp) { vm.setRedUp(true) }
                ColorOption("漲綠跌紅（美股慣例）", selected = !settings.redUp) { vm.setRedUp(false) }
            }

            // ---------- 關於 ----------
            Section("關於") {
                Hint(
                    "報價與 K 線來自富果行情 API（原始資料為臺灣證券交易所、證券櫃檯買賣中心）。" +
                        "股票清單、券商捷徑與設定只存在這支手機，不連任何券商帳號，也不記錄股數或成本。",
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPicker) {
        AppPickerDialog(
            existing = settings.brokers.map { it.packageName }.toSet(),
            onPick = { vm.addBroker(it) },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColorOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppPickerDialog(
    existing: Set<String>,
    onPick: (BrokerApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<BrokerApp>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("選擇券商 App", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Hint("名稱像券商的排在前面，可以選好幾個")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜尋 App 名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    val q = query.trim()
                    val filtered = remember(list, q) {
                        if (q.isEmpty()) {
                            list
                        } else {
                            list.filter {
                                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
                            }
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            val added = app.packageName in existing
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !added) { onPick(app) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (added) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已加入",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}
