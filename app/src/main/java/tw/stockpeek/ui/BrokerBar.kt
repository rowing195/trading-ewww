package tw.stockpeek.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.stockpeek.data.BrokerApp

/**
 * 底部券商捷徑列。點了直接開券商 App；在 K 線頁會順便把股票代號複製到剪貼簿，
 * 切過去搜尋框貼上就好。
 */
@Composable
fun BrokerBar(
    brokers: List<BrokerApp>,
    copySymbol: String?,
    onManage: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (brokers.isEmpty()) {
                if (onManage != null) {
                    TextButton(
                        onClick = onManage,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("＋ 加入券商 App 捷徑，一鍵切過去下單") }
                } else {
                    Text(
                        "到首頁右上角設定加入券商 App 捷徑",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Row(
                    Modifier.padding(start = 16.dp, top = 10.dp, bottom = 12.dp, end = if (onManage != null) 4.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("下單", style = MaterialTheme.typography.labelLarge)
                        if (copySymbol != null) {
                            Text(
                                "先複製 $copySymbol",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LazyRow(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(brokers, key = { it.packageName }) { app ->
                            BrokerChip(app) {
                                if (!launchApp(context, app.packageName, copySymbol)) {
                                    Toast.makeText(context, "打不開 ${app.label}，可能已解除安裝", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    if (onManage != null) {
                        IconButton(onClick = onManage) {
                            Icon(
                                AppIcons.Tune,
                                contentDescription = "管理券商捷徑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrokerChip(app: BrokerApp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                AppIcons.ArrowOutward,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

fun launchApp(context: Context, packageName: String, copySymbol: String?): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    if (copySymbol != null) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("股票代號", copySymbol))
        // Android 13 以上系統會自己跳出「已複製」提示
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "已複製 $copySymbol，到券商 App 貼上搜尋", Toast.LENGTH_SHORT).show()
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** 手機上所有可啟動的 App，名稱像券商的排前面。 */
fun launchableApps(context: Context): List<BrokerApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return queryActivities(pm, intent)
        .map { BrokerApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedWith(compareByDescending<BrokerApp> { looksLikeBroker(it.label) }.thenBy { it.label })
}

private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        queryActivitiesLegacy(pm, intent)
    }

@Suppress("DEPRECATION")
private fun queryActivitiesLegacy(pm: PackageManager, intent: Intent): List<ResolveInfo> =
    pm.queryIntentActivities(intent, 0)

private val BROKER_HINTS = listOf("證券", "證劵", "券商", "股", "期貨", "Securities", "Trade", "XQ", "e手掌握")

fun looksLikeBroker(label: String): Boolean = BROKER_HINTS.any { label.contains(it, ignoreCase = true) }
