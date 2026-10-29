package com.openticker.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openticker.app.data.model.*
import com.openticker.app.data.remote.HttpClient
import com.openticker.app.data.repository.*
import com.openticker.app.settings.*
import com.openticker.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

data class LeagueState(val games: List<Game> = emptyList(), val updated: Instant? = null, val error: String? = null)
data class DashboardState(
    val settings: AppSettings = AppSettings(), val settingsLoaded: Boolean = false,
    val scores: Map<League, LeagueState> = emptyMap(), val weather: Weather? = null,
    val weatherUpdated: Instant? = null, val weatherError: String? = null,
    val online: Boolean = false, val refreshing: Boolean = false,
    val searchResults: List<Location> = emptyList(), val searching: Boolean = false,
    val searchError: String? = null, val settingsError: String? = null
)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val http = HttpClient()
    private val sports: SportsRepository = EspnSportsRepository(http)
    private val weather: WeatherRepository = OpenMeteoWeatherRepository(http)
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    private val active = MutableStateFlow(false)
    private val refreshMutex = Mutex()
    private var searchJob: Job? = null
    private val sportsAttempts = mutableMapOf<League, Instant>()
    private var weatherAttempt: Pair<Location, Instant>? = null
    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                mutable.update { old ->
                    val changed = old.settings.location != settings.location
                    old.copy(settings = settings, settingsLoaded = true, weather = if (changed) null else old.weather,
                        weatherUpdated = if (changed) null else old.weatherUpdated, weatherError = if (changed) null else old.weatherError)
                }
            }
        }
        viewModelScope.launch { ConnectivityMonitor(application).online.collect { online -> mutable.update { it.copy(online = online) } } }
        viewModelScope.launch {
            combine(active, state.map { it.online }.distinctUntilChanged(),
                state.map { Triple(it.settingsLoaded, it.settings.location, it.settings.leagues to it.settings.sportsRefreshSeconds) }.distinctUntilChanged()) { visible, online, config ->
                visible && online && config.first
            }.collectLatest { run ->
                if (run) while (currentCoroutineContext().isActive) { refresh(false); delay(5_000) }
            }
        }
    }
    fun setActive(value: Boolean) { active.value = value }
    fun manualRefresh() { viewModelScope.launch { refresh(true) } }
    private suspend fun refresh(force: Boolean) {
        if (!state.value.online || !refreshMutex.tryLock()) return
        mutable.update { it.copy(refreshing = true) }
        try {
            val snapshot = state.value
            val now = Instant.now()
            supervisorScope {
                val jobs = snapshot.settings.leagues.mapNotNull { league ->
                    val previous = snapshot.scores[league]
                    val seconds = if (previous?.error != null || previous?.games?.any { it.status == GameStatus.LIVE } == true)
                        snapshot.settings.sportsRefreshSeconds else maxOf(180, snapshot.settings.sportsRefreshSeconds)
                    if (!force && sportsAttempts[league]?.let { now.epochSecond - it.epochSecond < seconds } == true) return@mapNotNull null
                    if (!force && previous?.updated != null && now.epochSecond - previous.updated.epochSecond < seconds) return@mapNotNull null
                    sportsAttempts[league] = now
                    launch {
                        try {
                            val games = sports.scoreboard(league)
                            mutable.update { it.copy(scores = it.scores + (league to LeagueState(games, Instant.now()))) }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            mutable.update { it.copy(scores = it.scores + (league to (it.scores[league] ?: LeagueState()).copy(error = "Sports feed unavailable · retrying"))) }
                        }
                    }
                }
                val weatherBackoff = weatherAttempt?.let { (location, time) -> location == snapshot.settings.location && now.epochSecond - time.epochSecond < 60 } == true
                val due = force || (!weatherBackoff && (snapshot.weatherUpdated == null || now.epochSecond - snapshot.weatherUpdated.epochSecond >= 900))
                if (due) launch {
                    weatherAttempt = snapshot.settings.location to now
                    try {
                        val forecast = weather.forecast(snapshot.settings.location)
                        mutable.update { if (it.settings.location == snapshot.settings.location) it.copy(weather = forecast, weatherUpdated = Instant.now(), weatherError = null) else it }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { mutable.update { it.copy(weatherError = "Weather feed unavailable · retrying") } }
                }
                jobs.joinAll()
            }
        } finally { mutable.update { it.copy(refreshing = false) }; refreshMutex.unlock() }
    }
    fun saveSettings(settings: AppSettings) { viewModelScope.launch {
        try { store.save(settings); mutable.update { it.copy(settingsError = null) } }
        catch (_: Exception) { mutable.update { it.copy(settingsError = "Could not save settings. Please try again.") } }
    } }
    fun clearSearch() { searchJob?.cancel(); mutable.update { it.copy(searchResults = emptyList(), searching = false, searchError = null) } }
    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) { clearSearch(); return }
        searchJob = viewModelScope.launch {
            mutable.update { it.copy(searching = true, searchError = null, searchResults = emptyList()) }
            delay(400)
            try {
                val results = weather.search(query)
                mutable.update { it.copy(searching = false, searchResults = results, searchError = if (results.isEmpty()) "No matches. Try a nearby city or city name." else null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(searching = false, searchError = "Location search unavailable. Please retry.") } }
        }
    }
}
