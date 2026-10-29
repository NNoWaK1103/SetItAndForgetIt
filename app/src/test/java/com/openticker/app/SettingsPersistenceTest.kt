package com.openticker.app

import androidx.datastore.preferences.core.*
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPersistenceTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun `fresh store defaults to ten second weather first automatic slideshow`() = runTest {
        val data=PreferenceDataStoreFactory.create(scope=backgroundScope,produceFile={File(folder.root,"defaults.preferences_pb")})
        assertEquals(AppSettings(),SettingsStore(data).settings.first())
    }
    @Test fun `all preferences survive closing and reopening a real DataStore file`() = runTest {
        val file=File(folder.root,"saved.preferences_pb")
        val expected=AppSettings(location=Location("Seattle","Washington",47.6,-122.3),celsius=true,
            leagues=setOf(League.NBA,League.NHL),sportsRefreshSeconds=60,tickerEnabled=false,keepAwake=false,
            slideshowIntervalSeconds=30,slideshowPaused=true,startSlide=DashboardSlide.SPORTS,
            weatherTickerEnabled=false,animationsEnabled=false,sportsGroupA=setOf(League.NBA,League.NHL),
            sportsGroupB=setOf(League.NFL,League.NCAAF,League.MLB),activeSportsGroup=SportsGroup.B,tickerAllLeagues=true)
        val firstJob=SupervisorJob()
        val firstScope=CoroutineScope(firstJob+StandardTestDispatcher(testScheduler))
        val first=SettingsStore(PreferenceDataStoreFactory.create(scope=firstScope,produceFile={file}))
        first.save(expected)
        assertEquals(expected,first.settings.first())
        firstJob.cancelAndJoin()
        val reopened=SettingsStore(PreferenceDataStoreFactory.create(scope=backgroundScope,produceFile={file}))
        assertEquals(expected,reopened.settings.first())
    }
    @Test fun `old preferences retain location units leagues and sports ticker choice`() = runTest {
        val data=PreferenceDataStoreFactory.create(scope=backgroundScope,produceFile={File(folder.root,"legacy.preferences_pb")})
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
    }
    @Test fun `invalid stored interval and start slide fall back safely`() = runTest {
        val data=PreferenceDataStoreFactory.create(scope=backgroundScope,produceFile={File(folder.root,"invalid.preferences_pb")})
        data.edit { it[intPreferencesKey("slideshow_interval")]=0;it[stringPreferencesKey("start_slide")]="UNKNOWN" }
        val restored=SettingsStore(data).settings.first()
        assertEquals(10,restored.slideshowIntervalSeconds);assertEquals(DashboardSlide.WEATHER,restored.startSlide)
    }
    @Test fun `selecting a group changes only its preference and survives reopening`() = runTest {
        val file=File(folder.root,"active-group.preferences_pb")
        val job=SupervisorJob()
        val scope=CoroutineScope(job+StandardTestDispatcher(testScheduler))
        val store=SettingsStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}))
        val saved=AppSettings(celsius=true,slideshowIntervalSeconds=15,sportsGroupA=setOf(League.NFL),sportsGroupB=setOf(League.NBA,League.NHL))
        store.save(saved);store.selectSportsGroup(SportsGroup.B)
        assertEquals(saved.copy(activeSportsGroup=SportsGroup.B),store.settings.first())
        job.cancelAndJoin()
        val reopened=SettingsStore(PreferenceDataStoreFactory.create(scope=backgroundScope,produceFile={file}))
        assertEquals(saved.copy(activeSportsGroup=SportsGroup.B),reopened.settings.first())
    }
    @Test fun `empty groups remain empty and unknown league names are ignored`() = runTest {
        val data=PreferenceDataStoreFactory.create(scope=backgroundScope,produceFile={File(folder.root,"group-validation.preferences_pb")})
        data.edit {
            it[stringSetPreferencesKey("sports_group_a")]=emptySet()
            it[stringSetPreferencesKey("sports_group_b")]=setOf("NBA","UNKNOWN")
            it[stringPreferencesKey("active_sports_group")]="INVALID"
        }
        val settings=SettingsStore(data).settings.first()
        assertTrue(settings.sportsGroupA.isEmpty());assertEquals(setOf(League.NBA),settings.sportsGroupB)
        assertEquals(SportsGroup.A,settings.activeSportsGroup)
    }
}
