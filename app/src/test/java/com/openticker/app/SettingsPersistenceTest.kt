package com.openticker.app

import androidx.datastore.preferences.core.*
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.openticker.app.data.model.*
import com.openticker.app.settings.*
import com.openticker.app.slideshow.DashboardSlide
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPersistenceTest {
    @get:Rule val folder=TemporaryFolder()
    // Android's FileStorage selects its pre-API-26 rename implementation in mockable android.jar.
    // Use real JVM filesystem IO with the same Preferences serializer for these host-side tests.
    private fun testStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope=scope, storage=OkioStorage(FileSystem.SYSTEM,
            object : OkioSerializer<Preferences> {
                override val defaultValue: Preferences = PreferencesFileSerializer.defaultValue
                override suspend fun readFrom(source: BufferedSource) = PreferencesFileSerializer.readFrom(source.inputStream())
                override suspend fun writeTo(t: Preferences, sink: BufferedSink) = PreferencesFileSerializer.writeTo(t,sink.outputStream())
            }, producePath={produceFile().toOkioPath()}))
    @Test fun `fresh store defaults to ten second weather first automatic slideshow`() = runTest {
        val data=testStore(scope=backgroundScope,produceFile={File(folder.root,"defaults.preferences_pb")})
        assertEquals(AppSettings(),SettingsStore(data).settings.first())
    }
    @Test fun `all preferences survive closing and reopening a real DataStore file`() = runTest {
        val file=File(folder.root,"saved.preferences_pb")
        val expected=AppSettings(location=Location("Seattle","Washington",47.6,-122.3),celsius=true,
            leagues=setOf(League.NBA,League.NHL),sportsRefreshSeconds=60,tickerEnabled=false,keepAwake=false,
            slideshowIntervalSeconds=30,slideshowPaused=true,startSlide=DashboardSlide.SPORTS,
            weatherTickerEnabled=false,animationsEnabled=false,sportsGroupA=setOf(League.NBA,League.NHL),
            sportsGroupB=setOf(League.NFL,League.NCAAF,League.MLB),activeSportsGroup=SportsGroup.B,tickerAllLeagues=true,
            enabledScreens=listOf(DashboardSlide.STOCKS,DashboardSlide.SPORTS),
            stocks=StockSettings(featured="MSFT",watchlist=listOf("HD","AAPL","BRK.B"),categories=setOf(StockCategory.FINANCIAL,StockCategory.RETAIL),
                range=ChartRange.YEAR,tickerEnabled=false,refreshSeconds=300,rotate=false,indices=setOf(MarketIndex.DOW),proxyUrl="https://example.com/market"))
        val firstJob=SupervisorJob()
        val firstScope=CoroutineScope(firstJob+StandardTestDispatcher(testScheduler))
        val first=SettingsStore(testStore(scope=firstScope,produceFile={file}))
        first.save(expected)
        assertEquals(expected,first.settings.first())
        firstJob.cancelAndJoin()
        val reopened=SettingsStore(testStore(scope=backgroundScope,produceFile={file}))
        assertEquals(expected,reopened.settings.first())
    }
    @Test fun `old preferences retain location units leagues and sports ticker choice`() = runTest {
        val data=testStore(scope=backgroundScope,produceFile={File(folder.root,"legacy.preferences_pb")})
        data.edit {
            it[stringPreferencesKey("city")]="Boston"
            it[booleanPreferencesKey("celsius")]=true
            it[booleanPreferencesKey("ticker")]=false
            it[stringSetPreferencesKey("leagues")]=setOf("NBA")
        }
        val restored=SettingsStore(data).settings.first()
        assertEquals("Boston",restored.location.name);assertTrue(restored.celsius)
        assertEquals(setOf(League.NBA),restored.leagues);assertFalse(restored.tickerEnabled)
        assertTrue(restored.weatherTickerEnabled);assertEquals(10,restored.slideshowIntervalSeconds)
        assertEquals(DashboardSlide.WEATHER,restored.startSlide)
        assertEquals(DEFAULT_GROUP_A,restored.sportsGroupA);assertEquals(DEFAULT_GROUP_B,restored.sportsGroupB)
        assertEquals(SportsGroup.A,restored.activeSportsGroup);assertFalse(restored.tickerAllLeagues)
        assertEquals(DashboardSlide.entries.toList(),restored.enabledScreens)
        assertEquals(StockSettings(),restored.stocks)
    }
    @Test fun `invalid stored interval and start slide fall back safely`() = runTest {
        val data=testStore(scope=backgroundScope,produceFile={File(folder.root,"invalid.preferences_pb")})
        data.edit { it[intPreferencesKey("slideshow_interval")]=0;it[stringPreferencesKey("start_slide")]="UNKNOWN" }
        val restored=SettingsStore(data).settings.first()
        assertEquals(10,restored.slideshowIntervalSeconds);assertEquals(DashboardSlide.WEATHER,restored.startSlide)
    }
    @Test fun `selecting a group changes only its preference and survives reopening`() = runTest {
        val file=File(folder.root,"active-group.preferences_pb")
        val job=SupervisorJob()
        val scope=CoroutineScope(job+StandardTestDispatcher(testScheduler))
        val store=SettingsStore(testStore(scope=scope,produceFile={file}))
        val saved=AppSettings(celsius=true,slideshowIntervalSeconds=15,sportsGroupA=setOf(League.NFL),sportsGroupB=setOf(League.NBA,League.NHL))
        store.save(saved);store.selectSportsGroup(SportsGroup.B)
        assertEquals(saved.copy(activeSportsGroup=SportsGroup.B),store.settings.first())
        job.cancelAndJoin()
        val reopened=SettingsStore(testStore(scope=backgroundScope,produceFile={file}))
        assertEquals(saved.copy(activeSportsGroup=SportsGroup.B),reopened.settings.first())
    }
    @Test fun `empty groups remain empty and unknown league names are ignored`() = runTest {
        val data=testStore(scope=backgroundScope,produceFile={File(folder.root,"group-validation.preferences_pb")})
        data.edit {
            it[stringSetPreferencesKey("sports_group_a")]=emptySet()
            it[stringSetPreferencesKey("sports_group_b")]=setOf("NBA","UNKNOWN")
            it[stringPreferencesKey("active_sports_group")]="INVALID"
        }
        val settings=SettingsStore(data).settings.first()
        assertTrue(settings.sportsGroupA.isEmpty());assertEquals(setOf(League.NBA),settings.sportsGroupB)
        assertEquals(SportsGroup.A,settings.activeSportsGroup)
    }
    @Test fun `invalid stock settings normalize and explicit empty lists persist`() = runTest {
        val data=testStore(scope=backgroundScope,produceFile={File(folder.root,"stocks.preferences_pb")})
        data.edit {
            it[stringPreferencesKey("stock_featured")]="../bad"
            it[stringPreferencesKey("stock_watchlist")]="brk.b,AAPL,AAPL,invalid!"
            it[stringPreferencesKey("stock_range")]="invalid"
            it[intPreferencesKey("stock_refresh")]=1
            it[stringPreferencesKey("stock_proxy_url")]="http://example.com?apikey=bad"
            it[stringPreferencesKey("enabled_screens_order")]="STOCKS,INVALID,WEATHER,STOCKS"
        }
        val store=SettingsStore(data);val s=store.settings.first()
        assertEquals("HD",s.stocks.featured);assertEquals(listOf("BRK.B","AAPL"),s.stocks.watchlist)
        assertEquals(ChartRange.DAY,s.stocks.range);assertEquals(60,s.stocks.refreshSeconds);assertEquals("",s.stocks.proxyUrl)
        assertEquals(listOf(DashboardSlide.STOCKS,DashboardSlide.WEATHER),s.enabledScreens)
        store.save(s.copy(enabledScreens=emptyList(),stocks=s.stocks.copy(watchlist=emptyList(),categories=emptySet(),indices=emptySet())))
        val empty=store.settings.first();assertTrue(empty.enabledScreens.isEmpty());assertTrue(empty.stocks.watchlist.isEmpty())
        assertTrue(empty.stocks.categories.isEmpty());assertTrue(empty.stocks.indices.isEmpty())
    }
    @Test fun `chart range selection changes only its preference`() = runTest {
        val store=SettingsStore(testStore(scope=backgroundScope,produceFile={File(folder.root,"stock-range.preferences_pb")}))
        val saved=AppSettings(celsius=true,slideshowIntervalSeconds=30,stocks=StockSettings(watchlist=listOf("AAPL","BRK.B")))
        store.save(saved);store.selectStockRange(ChartRange.QUARTER)
        assertEquals(saved.copy(stocks=saved.stocks.copy(range=ChartRange.QUARTER)),store.settings.first())
    }
}
