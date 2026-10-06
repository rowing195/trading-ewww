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

@Composable
fun StockPeekApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(HOME) }
    val lifecycleOwner = LocalLifecycleOwner.current

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
            BackHandler(enabled = route != HOME) { route = HOME }
            when {
                route == SETTINGS -> SettingsScreen(vm, onBack = { route = HOME })
                route.startsWith(STOCK_PREFIX) -> DetailScreen(
                    vm = vm,
                    symbol = route.removePrefix(STOCK_PREFIX),
                    onBack = { route = HOME },
                )
                else -> HomeScreen(
                    vm = vm,
                    onOpen = { route = STOCK_PREFIX + it },
                    onSettings = { route = SETTINGS },
                )
            }
        }
    }
}
