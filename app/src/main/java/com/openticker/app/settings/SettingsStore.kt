package com.openticker.app.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import com.openticker.app.data.model.*
import com.openticker.app.slideshow.DashboardSlide
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore("openticker_settings")
data class AppSettings(
    val location: Location = Location.DEFAULT,
    val celsius: Boolean = false,
    val leagues: Set<League> = League.entries.toSet(),
    val sportsRefreshSeconds: Int = 30,
    val tickerEnabled: Boolean = true,
    val keepAwake: Boolean = true,
    val slideshowIntervalSeconds: Int = 10,
    val slideshowPaused: Boolean = false,
    val startSlide: DashboardSlide = DashboardSlide.WEATHER,
    val weatherTickerEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val sportsGroupA: Set<League> = DEFAULT_GROUP_A,
    val sportsGroupB: Set<League> = DEFAULT_GROUP_B,
    val activeSportsGroup: SportsGroup = SportsGroup.A,
    val tickerAllLeagues: Boolean = false,
    val enabledScreens: List<DashboardSlide> = DashboardSlide.entries.toList(),
    val stocks: StockSettings = StockSettings()
)
val SLIDESHOW_INTERVALS = listOf(5, 10, 15, 30, 60)
class SettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)
    private object Keys {
        val name = stringPreferencesKey("city"); val region = stringPreferencesKey("region")
        val latitude = doublePreferencesKey("latitude"); val longitude = doublePreferencesKey("longitude")
        val celsius = booleanPreferencesKey("celsius"); val leagues = stringSetPreferencesKey("leagues")
        val refresh = intPreferencesKey("sports_refresh"); val ticker = booleanPreferencesKey("ticker")
        val awake = booleanPreferencesKey("keep_awake")
        val interval = intPreferencesKey("slideshow_interval")
        val paused = booleanPreferencesKey("slideshow_paused")
        val start = stringPreferencesKey("start_slide")
        val weatherTicker = booleanPreferencesKey("weather_ticker")
        val animations = booleanPreferencesKey("animations")
        val groupA = stringSetPreferencesKey("sports_group_a")
        val groupB = stringSetPreferencesKey("sports_group_b")
        val activeGroup = stringPreferencesKey("active_sports_group")
        val tickerAll = booleanPreferencesKey("ticker_all_leagues")
        val screens = stringPreferencesKey("enabled_screens_order")
        val featured = stringPreferencesKey("stock_featured")
        val watchlist = stringPreferencesKey("stock_watchlist")
        val categories = stringSetPreferencesKey("stock_categories")
        val range = stringPreferencesKey("stock_range")
        val stockTicker = booleanPreferencesKey("stock_ticker")
        val stockRefresh = intPreferencesKey("stock_refresh")
        val stockRotate = booleanPreferencesKey("stock_rotate")
        val indices = stringSetPreferencesKey("stock_indices")
        val proxy = stringPreferencesKey("stock_proxy_url")
    }
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        AppSettings(location = Location(p[Keys.name] ?: Location.DEFAULT.name, p[Keys.region] ?: Location.DEFAULT.region,
            p[Keys.latitude] ?: Location.DEFAULT.latitude, p[Keys.longitude] ?: Location.DEFAULT.longitude),
            celsius = p[Keys.celsius] ?: false,
            leagues = p[Keys.leagues]?.let { names -> League.entries.filter { it.name in names }.toSet() } ?: League.entries.toSet(),
            sportsRefreshSeconds = (p[Keys.refresh] ?: 30).coerceIn(30, 300),
            tickerEnabled = p[Keys.ticker] ?: true, keepAwake = p[Keys.awake] ?: true,
            slideshowIntervalSeconds = p[Keys.interval]?.takeIf { it in SLIDESHOW_INTERVALS } ?: 10,
            slideshowPaused = p[Keys.paused] ?: false,
            startSlide = DashboardSlide.entries.find { it.name == p[Keys.start] } ?: DashboardSlide.WEATHER,
            weatherTickerEnabled = p[Keys.weatherTicker] ?: true,
            animationsEnabled = p[Keys.animations] ?: true,
            sportsGroupA = p[Keys.groupA]?.let { names -> League.entries.filter { it.name in names }.toSet() } ?: DEFAULT_GROUP_A,
            sportsGroupB = p[Keys.groupB]?.let { names -> League.entries.filter { it.name in names }.toSet() } ?: DEFAULT_GROUP_B,
            activeSportsGroup = SportsGroup.entries.find { it.name == p[Keys.activeGroup] } ?: SportsGroup.A,
            tickerAllLeagues = p[Keys.tickerAll] ?: false,
            enabledScreens = p[Keys.screens]?.let { value -> value.split(',').mapNotNull { name -> DashboardSlide.entries.find { it.name == name } }.distinct() }
                ?: DashboardSlide.entries.toList(),
            stocks = StockSettings(
                featured = p[Keys.featured] ?: "HD",
                watchlist = p[Keys.watchlist]?.split(',') ?: StockSettings().watchlist,
                categories = p[Keys.categories]?.let { names -> StockCategory.entries.filter { it.name in names }.toSet() } ?: StockCategory.entries.toSet(),
                range = ChartRange.entries.find { it.name == p[Keys.range] } ?: ChartRange.DAY,
                tickerEnabled = p[Keys.stockTicker] ?: true, refreshSeconds = p[Keys.stockRefresh] ?: 60,
                rotate = p[Keys.stockRotate] ?: true,
                indices = p[Keys.indices]?.let { names -> MarketIndex.entries.filter { it.name in names }.toSet() } ?: MarketIndex.entries.toSet(),
                proxyUrl = p[Keys.proxy] ?: "").normalized())
    }
    suspend fun save(value: AppSettings) { store.edit { p ->
        p[Keys.name] = value.location.name; p[Keys.region] = value.location.region
        p[Keys.latitude] = value.location.latitude; p[Keys.longitude] = value.location.longitude
        p[Keys.celsius] = value.celsius; p[Keys.leagues] = value.leagues.map { it.name }.toSet()
        p[Keys.refresh] = value.sportsRefreshSeconds; p[Keys.ticker] = value.tickerEnabled; p[Keys.awake] = value.keepAwake
        p[Keys.interval] = value.slideshowIntervalSeconds.takeIf { it in SLIDESHOW_INTERVALS } ?: 10
        p[Keys.paused] = value.slideshowPaused; p[Keys.start] = value.startSlide.name
        p[Keys.weatherTicker] = value.weatherTickerEnabled; p[Keys.animations] = value.animationsEnabled
        p[Keys.groupA] = value.sportsGroupA.map { it.name }.toSet()
        p[Keys.groupB] = value.sportsGroupB.map { it.name }.toSet()
        p[Keys.activeGroup] = value.activeSportsGroup.name; p[Keys.tickerAll] = value.tickerAllLeagues
        p[Keys.screens] = value.enabledScreens.distinct().joinToString(",") { it.name }
        val stocks = value.stocks.normalized()
        p[Keys.featured] = stocks.featured; p[Keys.watchlist] = stocks.watchlist.joinToString(",")
        p[Keys.categories] = stocks.categories.map { it.name }.toSet(); p[Keys.range] = stocks.range.name
        p[Keys.stockTicker] = stocks.tickerEnabled; p[Keys.stockRefresh] = stocks.refreshSeconds
        p[Keys.stockRotate] = stocks.rotate; p[Keys.indices] = stocks.indices.map { it.name }.toSet()
        p[Keys.proxy] = stocks.proxyUrl
    } }
    suspend fun selectSportsGroup(group: SportsGroup) { store.edit { it[Keys.activeGroup] = group.name } }
    suspend fun selectStockRange(range: ChartRange) { store.edit { it[Keys.range] = range.name } }
}
