package com.openticker.app.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.openticker.app.data.model.*
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
    val keepAwake: Boolean = true
)
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private object Keys {
        val name = stringPreferencesKey("city"); val region = stringPreferencesKey("region")
        val latitude = doublePreferencesKey("latitude"); val longitude = doublePreferencesKey("longitude")
        val celsius = booleanPreferencesKey("celsius"); val leagues = stringSetPreferencesKey("leagues")
        val refresh = intPreferencesKey("sports_refresh"); val ticker = booleanPreferencesKey("ticker")
        val awake = booleanPreferencesKey("keep_awake")
    }
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        AppSettings(Location(p[Keys.name] ?: Location.DEFAULT.name, p[Keys.region] ?: Location.DEFAULT.region,
            p[Keys.latitude] ?: Location.DEFAULT.latitude, p[Keys.longitude] ?: Location.DEFAULT.longitude),
            p[Keys.celsius] ?: false,
            p[Keys.leagues]?.let { names -> League.entries.filter { it.name in names }.toSet() } ?: League.entries.toSet(),
            (p[Keys.refresh] ?: 30).coerceIn(30, 300), p[Keys.ticker] ?: true, p[Keys.awake] ?: true)
    }
    suspend fun save(value: AppSettings) { store.edit { p ->
        p[Keys.name] = value.location.name; p[Keys.region] = value.location.region
        p[Keys.latitude] = value.location.latitude; p[Keys.longitude] = value.location.longitude
        p[Keys.celsius] = value.celsius; p[Keys.leagues] = value.leagues.map { it.name }.toSet()
        p[Keys.refresh] = value.sportsRefreshSeconds; p[Keys.ticker] = value.tickerEnabled; p[Keys.awake] = value.keepAwake
    } }
}
