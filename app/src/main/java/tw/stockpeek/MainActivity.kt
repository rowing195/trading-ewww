package tw.stockpeek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import tw.stockpeek.data.MarketClock
import tw.stockpeek.ui.DetailScreen
import tw.stockpeek.ui.HomeScreen
import tw.stockpeek.ui.SettingsScreen
import tw.stockpeek.ui.SignalsScreen
import tw.stockpeek.ui.theme.StockPeekTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StockPeekApp() }
    }
}

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val STOCK_PREFIX = "stock:"
private const val SIGNALS_PREFIX = "signals:"

@Composable
fun StockPeekApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(HOME) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val saveableState = rememberSaveableStateHolder()

    // 只有「K 線 → 技術面摘要」保留 K 線頁的狀態（週期、副圖），回來時原樣；其他離開的頁面都清掉
    fun navigate(to: String) {
        if (!(route.startsWith(STOCK_PREFIX) && to.startsWith(SIGNALS_PREFIX))) saveableState.removeState(route)
        route = to
    }

    // App 在前景時：一打開（含從券商 App 切回來）就更新一次，盤中再定時更新
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshNow()
            while (true) {
                delay(if (MarketClock.isActive()) vm.autoRefreshDelayMs() else 60_000L)
                if (MarketClock.isActive()) vm.refreshNow()
            }
        }
    }

    StockPeekTheme(redUp = settings.redUp) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BackHandler(enabled = route != HOME) {
                navigate(if (route.startsWith(SIGNALS_PREFIX)) STOCK_PREFIX + route.removePrefix(SIGNALS_PREFIX) else HOME)
            }
            saveableState.SaveableStateProvider(route) {
                when {
                    route == SETTINGS -> SettingsScreen(vm, onBack = { navigate(HOME) })
                    route.startsWith(SIGNALS_PREFIX) -> {
                        val symbol = route.removePrefix(SIGNALS_PREFIX)
                        SignalsScreen(vm = vm, symbol = symbol, onBack = { navigate(STOCK_PREFIX + symbol) })
                    }
                    route.startsWith(STOCK_PREFIX) -> {
                        val symbol = route.removePrefix(STOCK_PREFIX)
                        DetailScreen(
                            vm = vm,
                            symbol = symbol,
                            onBack = { navigate(HOME) },
                            onSummary = { navigate(SIGNALS_PREFIX + symbol) },
                        )
                    }
                    else -> HomeScreen(
                        vm = vm,
                        onOpen = { navigate(STOCK_PREFIX + it) },
                        onSettings = { navigate(SETTINGS) },
                    )
                }
            }
        }
    }
}
