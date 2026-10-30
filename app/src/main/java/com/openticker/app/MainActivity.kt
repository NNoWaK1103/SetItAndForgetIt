package com.openticker.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.openticker.app.ui.screens.OpenTickerApp
import com.openticker.app.ui.theme.OpenTickerTheme
import com.openticker.app.viewmodel.DashboardViewModel
import com.openticker.app.viewmodel.StockMarketViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: DashboardViewModel by viewModels()
    private val stocks: StockMarketViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.setActive(true)
                stocks.setActive(true)
                try { awaitCancellation() } finally { model.setActive(false); stocks.setActive(false) }
            }
        }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.settings.keepAwake) {
                if(state.settings.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            OpenTickerTheme { OpenTickerApp(model,stocks) }
        }
    }
    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) immersive() }
}
