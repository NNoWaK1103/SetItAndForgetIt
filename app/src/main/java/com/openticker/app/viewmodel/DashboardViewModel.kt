package com.openticker.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openticker.app.data.model.*
import com.openticker.app.data.remote.HttpClient
import com.openticker.app.data.remote.feedFailureMessage
import java.io.File
import com.openticker.app.data.repository.*
import com.openticker.app.settings.*
import com.openticker.app.slideshow.*
import com.openticker.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.LocalDate

data class LeagueState(val games: List<Game> = emptyList(), val updated: Instant? = null, val error: String? = null, val restored: Boolean = false,
    val scheduleDate: LocalDate? = null, val loading: Boolean = false)
data class DashboardState(
    val settings: AppSettings = AppSettings(), val settingsLoaded: Boolean = false,
    val scores: Map<League, LeagueState> = emptyMap(), val weather: Weather? = null,
    val weatherUpdated: Instant? = null, val weatherError: String? = null, val weatherRestored: Boolean = false,
    val online: Boolean = false, val refreshing: Boolean = false,
    val searchResults: List<Location> = emptyList(), val searching: Boolean = false,
    val searchError: String? = null, val settingsError: String? = null,
    val settingsSaving: Boolean = false
)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val http = HttpClient()
    private val cache = LastGoodCache(File(application.filesDir, "last-good"))
    private val sports: SportsRepository = EspnSportsRepository(http)
    private val weather: WeatherRepository = OpenMeteoWeatherRepository(http)
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    private val active = MutableStateFlow(false)
    private val slideshowController = SlideshowController(viewModelScope)
    val slideshow = slideshowController.state
    private val refreshMutex = Mutex()
    private var searchJob: Job? = null
    private val sportsAttempts = mutableMapOf<League, SportsScheduleAttempt>()
    private var weatherAttempt: Pair<Location, Instant>? = null
    init {
        // Re-evaluate freshness and the Eastern date without changing saved settings or polling.
        val scheduleClock = MutableStateFlow(Instant.now())
        viewModelScope.launch {
            active.collectLatest { visible ->
                scheduleClock.value = Instant.now()
                if (visible) while (currentCoroutineContext().isActive) {
                    delay(1_000)
                    scheduleClock.value = Instant.now()
                }
            }
        }
        viewModelScope.launch {
            combine(state, scheduleClock) { current, now ->
                if (current.settingsLoaded) sportsPlaybackSettings(current.settings, current.scores, current.online, now) else null
            }.filterNotNull().distinctUntilChanged().collect { slideshowController.configure(it) }
        }
        viewModelScope.launch {
            store.settings.collect { settings ->
                val old = state.value
                val changed = !old.settingsLoaded || old.settings.location != settings.location
                val savedWeather = if (changed) cache.read<WeatherSnapshot>(weatherCacheKey(settings.location))?.takeIf {
                    it.updated <= Instant.now() && it.location.latitude == settings.location.latitude && it.location.longitude == settings.location.longitude
                } else null
                val savedScores = if (!old.settingsLoaded) League.entries.mapNotNull { league ->
                    cache.read<ScoreSnapshot>(sportsCacheKey(league))?.takeIf { it.updated <= Instant.now() }?.let {
                        league to LeagueState(it.games, it.updated, restored = true)
                    }
                }.toMap() else old.scores
                mutable.update { current ->
                    current.copy(settings = settings, settingsLoaded = true, scores = if (!old.settingsLoaded) savedScores else current.scores,
                        weather = if (changed) savedWeather?.weather else current.weather,
                        weatherUpdated = if (changed) savedWeather?.updated else current.weatherUpdated,
                        weatherRestored = if (changed) savedWeather != null else current.weatherRestored,
                        weatherError = if (changed) null else current.weatherError)
                }
            }
        }
        viewModelScope.launch { ConnectivityMonitor(application).online.collect { online -> mutable.update { it.copy(online = online) } } }
        viewModelScope.launch {
            combine(active, state.map { it.online }.distinctUntilChanged(),
                state.map { Triple(it.settingsLoaded, it.settings.location, it.settings.polledLeagues() to it.settings.sportsRefreshSeconds) }.distinctUntilChanged()) { visible, online, config ->
                visible && online && config.first
            }.collectLatest { run ->
                if (run) while (currentCoroutineContext().isActive) { refresh(false); delay(5_000) }
            }
        }
    }
    fun setActive(value: Boolean) { active.value = value; slideshowController.setForeground(value) }
    fun setPlaybackInteraction(held: Boolean) { slideshowController.setInteractionHeld(held) }
    fun selectSlide(slide: DashboardSlide) { slideshowController.select(slide) }
    fun toggleSlideshow() { viewModelScope.launch {
        try { store.toggleSlideshowPaused(); mutable.update { it.copy(settingsError = null) } }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { mutable.update { it.copy(settingsError = "Could not save playback state. Please try again.") } }
    } }
    fun selectSlideshowInterval(seconds: Int) { viewModelScope.launch {
        try { store.selectSlideshowInterval(seconds); mutable.update { it.copy(settingsError = null) } }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { mutable.update { it.copy(settingsError = "Could not save slideshow interval. Please try again.") } }
    } }
    fun selectSportsGroup(group: SportsGroup) { viewModelScope.launch {
        try { store.selectSportsGroup(group); mutable.update { it.copy(settingsError = null) } }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { mutable.update { it.copy(settingsError = "Could not save sports group. Please try again.") } }
    } }
    fun manualRefresh() { viewModelScope.launch { refresh(true) } }
    private suspend fun refresh(force: Boolean) {
        if (!state.value.online || !refreshMutex.tryLock()) return
        mutable.update { it.copy(refreshing = true) }
        try {
            val snapshot = state.value
            val now = Instant.now()
            supervisorScope {
                val jobs = snapshot.settings.polledLeagues().mapNotNull { league ->
                    val previous = snapshot.scores[league]
                    val seconds = if (previous?.error != null || previous?.games?.any { it.status == GameStatus.LIVE } == true)
                        snapshot.settings.sportsRefreshSeconds else maxOf(180, snapshot.settings.sportsRefreshSeconds)
                    if (!force && !isSportsScheduleRefreshDue(previous, sportsAttempts[league], now, seconds)) return@mapNotNull null
                    val scheduleDate = sportsScheduleDate(now)
                    sportsAttempts[league] = SportsScheduleAttempt(scheduleDate, now)
                    launch {
                        try {
                            mutable.update { it.copy(scores = it.scores + (league to (it.scores[league] ?: LeagueState()).copy(loading = true))) }
                            val games = sports.scoreboard(league, scheduleDate)
                            val updated = Instant.now()
                            mutable.update { it.copy(scores = it.scores + (league to LeagueState(games, updated, scheduleDate = scheduleDate))) }
                            cache.write(sportsCacheKey(league), ScoreSnapshot(games, updated))
                        } catch (e: CancellationException) { sportsAttempts.remove(league); throw e }
                        catch (e: Exception) {
                            mutable.update { it.copy(scores = it.scores + (league to (it.scores[league] ?: LeagueState()).copy(error = feedFailureMessage(e, league.name)))) }
                        } finally {
                            mutable.update { it.copy(scores = it.scores + (league to (it.scores[league] ?: LeagueState()).copy(loading = false))) }
                        }
                    }
                }
                val weatherBackoff = weatherAttempt?.let { (location, time) -> location == snapshot.settings.location && now.epochSecond - time.epochSecond < 60 } == true
                val due = force || (!weatherBackoff && (snapshot.weatherRestored || snapshot.weatherUpdated == null || now.epochSecond - snapshot.weatherUpdated.epochSecond >= 900))
                if (due) launch {
                    weatherAttempt = snapshot.settings.location to now
                    try {
                        val forecast = weather.forecast(snapshot.settings.location)
                        val updated = Instant.now()
                        mutable.update { if (it.settings.location == snapshot.settings.location) it.copy(weather = forecast, weatherUpdated = updated, weatherError = null, weatherRestored = false) else it }
                        cache.write(weatherCacheKey(snapshot.settings.location), WeatherSnapshot(snapshot.settings.location, forecast, updated))
                    } catch (e: CancellationException) { weatherAttempt = null; throw e }
                    catch (e: Exception) { mutable.update { if (it.settings.location == snapshot.settings.location) it.copy(weatherError = feedFailureMessage(e, "Weather")) else it } }
                }
                jobs.joinAll()
            }
        } finally { mutable.update { it.copy(refreshing = false) }; refreshMutex.unlock() }
    }
    private val settingsSaver = SettingsSaveController(viewModelScope, store::save,
        onSaving = { saving -> mutable.update { it.copy(settingsSaving = saving) } },
        onError = { error -> mutable.update { it.copy(settingsError = error) } })
    fun saveSettings(settings: AppSettings, onSaved: () -> Unit) { settingsSaver.save(settings, onSaved) }
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
