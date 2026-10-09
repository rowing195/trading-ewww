package tw.stockpeek.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 所有設定都只存在這支手機上（DataStore），API 金鑰另外用 Keystore 加密。 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val WATCHLIST = stringPreferencesKey("watchlist")
        val BROKERS = stringPreferencesKey("brokers")
        val API_KEY = stringPreferencesKey("fugle_api_key_enc")
        val MA = stringPreferencesKey("ma_periods")
        val SHOW_VOLUME = booleanPreferencesKey("show_volume")
        val SHOW_HILO = booleanPreferencesKey("show_hilo")
        val SHOW_BOLLINGER = booleanPreferencesKey("show_bollinger")
        val SUB_INDICATORS = stringPreferencesKey("sub_indicators")
        val RED_UP = booleanPreferencesKey("red_up")
        val SORT = stringPreferencesKey("sort_mode")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                loaded = true,
                hasApiKey = !p[Keys.API_KEY].isNullOrBlank(),
                watchlist = decodePairs(p[Keys.WATCHLIST]).map { (a, b) -> WatchItem(a, b) },
                brokers = decodePairs(p[Keys.BROKERS]).map { (a, b) -> BrokerApp(a, b) },
                maPeriods = p[Keys.MA]?.let(::parseMaPeriods) ?: DEFAULT_MA,
                showVolume = p[Keys.SHOW_VOLUME] ?: true,
                showHiLo = p[Keys.SHOW_HILO] ?: true,
                showBollinger = p[Keys.SHOW_BOLLINGER] ?: false,
                subIndicators = p[Keys.SUB_INDICATORS]
                    ?.let { raw -> SubIndicator.entries.filter { it.name in raw.split(',') } }
                    ?: SubIndicator.entries,
                redUp = p[Keys.RED_UP] ?: true,
                sortMode = p[Keys.SORT]
                    ?.let { s -> SortMode.entries.firstOrNull { it.name == s } }
                    ?: SortMode.CUSTOM,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun apiKey(): String? = store.data.first()[Keys.API_KEY]?.let(KeyCipher::decrypt)

    suspend fun setApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = if (trimmed.isEmpty()) null else withContext(Dispatchers.Default) { KeyCipher.encrypt(trimmed) }
        store.edit { p ->
            p.remove(Keys.API_KEY)
            if (encrypted != null) p[Keys.API_KEY] = encrypted
        }
    }

    suspend fun updateWatchlist(transform: (List<WatchItem>) -> List<WatchItem>) {
        store.edit { p ->
            val current = decodePairs(p[Keys.WATCHLIST]).map { (a, b) -> WatchItem(a, b) }
            p[Keys.WATCHLIST] = encodePairs(transform(current).map { it.symbol to it.name })
        }
    }

    suspend fun updateBrokers(transform: (List<BrokerApp>) -> List<BrokerApp>) {
        store.edit { p ->
            val current = decodePairs(p[Keys.BROKERS]).map { (a, b) -> BrokerApp(a, b) }
            p[Keys.BROKERS] = encodePairs(transform(current).map { it.packageName to it.label })
        }
    }

    suspend fun setMaPeriods(periods: List<Int>) {
        store.edit { it[Keys.MA] = periods.joinToString(",") }
    }

    suspend fun setShowVolume(show: Boolean) {
        store.edit { it[Keys.SHOW_VOLUME] = show }
    }

    suspend fun setShowHiLo(show: Boolean) {
        store.edit { it[Keys.SHOW_HILO] = show }
    }

    suspend fun setShowBollinger(show: Boolean) {
        store.edit { it[Keys.SHOW_BOLLINGER] = show }
    }

    suspend fun setSubIndicators(list: List<SubIndicator>) {
        store.edit { it[Keys.SUB_INDICATORS] = list.joinToString(",") { s -> s.name } }
    }

    suspend fun setRedUp(redUp: Boolean) {
        store.edit { it[Keys.RED_UP] = redUp }
    }

    /** K 線相關設定全部回到預設值。 */
    suspend fun resetChartSettings() {
        store.edit { p ->
            listOf(Keys.MA, Keys.SHOW_VOLUME, Keys.SHOW_HILO, Keys.SHOW_BOLLINGER, Keys.SUB_INDICATORS, Keys.RED_UP)
                .forEach { p.remove(it) }
        }
    }

    suspend fun setSortMode(mode: SortMode) {
        store.edit { it[Keys.SORT] = mode.name }
    }

    // 一行一筆「a<TAB>b」
    private fun encodePairs(items: List<Pair<String, String>>): String =
        items.joinToString("\n") { (a, b) -> "${clean(a)}\t${clean(b)}" }

    private fun decodePairs(raw: String?): List<Pair<String, String>> =
        raw.orEmpty().lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                val a = parts.getOrNull(0)?.trim().orEmpty()
                if (a.isEmpty()) null else a to (parts.getOrNull(1)?.trim()?.ifEmpty { a } ?: a)
            }
            .toList()

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
