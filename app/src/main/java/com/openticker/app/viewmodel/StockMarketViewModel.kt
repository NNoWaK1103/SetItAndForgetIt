package com.openticker.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openticker.app.data.model.ChartRange
import com.openticker.app.data.remote.*
import com.openticker.app.data.repository.*
import com.openticker.app.settings.SettingsStore
import com.openticker.app.util.ConnectivityMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.io.File

class StockMarketViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val repository: StockRepository = CachedStockRepository(ProxyStockDataSource(HttpClient()),
        LastGoodCache(File(application.filesDir, "last-good")))
    private val active = MutableStateFlow(false)
    private val mutable = MutableStateFlow(StockMarketUiState())
    val state = mutable.asStateFlow()
    private val page = MutableStateFlow(0)
    val appearance = page.asStateFlow()
    private var appeared = false
    init {
        viewModelScope.launch {
            combine(active, ConnectivityMonitor(application).online, store.settings) { visible, online, settings ->
                Triple(visible, online, settings.stocks)
            }.distinctUntilChanged().collectLatest { (visible, online, settings) ->
                val restored = repository.restore(settings)
                mutable.value = restored.copy(online = online, refreshing = false,
                    staleSymbols = if (online) restored.staleSymbols else restored.quotes.keys)
                if (visible && online) while (currentCoroutineContext().isActive) {
                    mutable.value = mutable.value.copy(refreshing = true)
                    mutable.value = repository.refresh(settings, Instant.now()).copy(online = online, refreshing = false)
                    delay(5_000)
                }
            }
        }
    }
    fun setActive(value: Boolean) { active.value = value }
    fun onStockAppearance() { if (appeared) page.value++ else appeared = true }
    fun selectRange(range: ChartRange) { viewModelScope.launch {
        try { store.selectStockRange(range) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { mutable.update { it.copy(error="Could not save chart range. Please try again.") } }
    } }
}
