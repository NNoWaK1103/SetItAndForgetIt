package com.openticker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.settings.*
import com.openticker.app.slideshow.DashboardSlide
import com.openticker.app.ui.theme.*
import com.openticker.app.viewmodel.DashboardState

@Composable fun SettingsDialog(state: DashboardState, onSearch: (String) -> Unit, onSave: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(state.settings) }
    var query by remember { mutableStateOf("") }
    var featured by remember { mutableStateOf(draft.stocks.featured) }
    var watchlist by remember { mutableStateOf(draft.stocks.watchlist.joinToString(", ")) }
    var proxy by remember { mutableStateOf(draft.stocks.proxyUrl) }
    val parsedWatchlist = watchlist.split(',', ' ', '\n').filter { it.isNotBlank() }
    val stocksValid = normalizeStockSymbol(featured)!=null && parsedWatchlist.size<=50 && parsedWatchlist.all { normalizeStockSymbol(it)!=null } && (proxy.isBlank() || validStockProxy(proxy.trim()))
    AlertDialog(onDismissRequest=onDismiss, title={ Text("Make it yours") },
        text={ Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Automatic slideshow",color=Mint,fontSize=13.sp)
            Text("Enabled screens (in playback order)",color=Mint,fontSize=13.sp)
            DashboardSlide.entries.forEach { slide ->
                SettingToggle(slide.label,slide in draft.enabledScreens) { enabled ->
                    draft=draft.copy(enabledScreens=if(enabled) draft.enabledScreens+slide else draft.enabledScreens-slide)
                }
            }
            Text("${draft.enabledScreens.joinToString(" → ") { it.label }}. Re-enable a screen to place it last. If all are disabled, playback stops.",color=Muted,fontSize=11.sp)
            SettingToggle("Pause automatic switching",draft.slideshowPaused) {draft=draft.copy(slideshowPaused=it)}
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { SLIDESHOW_INTERVALS.forEach { seconds ->
                FilterChip(selected=draft.slideshowIntervalSeconds==seconds,onClick={draft=draft.copy(slideshowIntervalSeconds=seconds)},label={Text("${seconds}s",fontSize=10.sp)})
            } }
            Text("Start on",color=Mint,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { DashboardSlide.entries.forEach { slide ->
                FilterChip(selected=draft.startSlide==slide,onClick={draft=draft.copy(startSlide=slide)},label={Text(slide.label)})
            } }
            Text("The start screen applies next time the app launches. Manual switches get a full display interval.",color=Muted,fontSize=11.sp)
            SettingToggle("Animations and ticker scrolling",draft.animationsEnabled) {draft=draft.copy(animationsEnabled=it)}
            Text("Weather location",color=Mint,fontSize=13.sp)
            Text(draft.location.label,fontSize=13.sp)
            OutlinedTextField(value=query,onValueChange={query=it; onSearch(it)},label={Text("City or ZIP code")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.searchError?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
            state.searchResults.forEach { location -> TextButton(onClick={draft=draft.copy(location=location)}) { Text(location.label,fontSize=12.sp) } }
            SettingToggle("Celsius (°C)",draft.celsius) {draft=draft.copy(celsius=it)}
            Text("Sports groups",color=Mint,fontSize=13.sp)
            Text("Select a group now. Group changes leave the slideshow timer running.",color=Muted,fontSize=11.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { SportsGroup.entries.forEach { group ->
                FilterChip(selected=draft.activeSportsGroup==group,onClick={draft=draft.copy(activeSportsGroup=group)},label={Text("Group ${group.name}")})
            } }
            SportsGroup.entries.forEach { group ->
                Text("Group ${group.name} leagues",color=Mint,fontSize=12.sp)
                LeagueSelection(draft.groupMembers(group)) { members ->
                    draft=if(group==SportsGroup.A) draft.copy(sportsGroupA=members) else draft.copy(sportsGroupB=members)
                }
            }
            SettingToggle("All five leagues in sports ticker",draft.tickerAllLeagues) {draft=draft.copy(tickerAllLeagues=it)}
            Text("By default, the score wire follows the selected group. All-five mode also includes disabled feeds in the ticker. Groups switch manually.",color=Muted,fontSize=11.sp)
            Text("Enabled sports feeds",color=Mint,fontSize=13.sp)
            Text("Disabled feeds are hidden in both dashboard groups.",color=Muted,fontSize=11.sp)
            LeagueSelection(draft.leagues) {draft=draft.copy(leagues=it)}
            Text("Live sports refresh",color=Mint,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { listOf(30,60,120,300).forEach { seconds ->
                FilterChip(selected=draft.sportsRefreshSeconds==seconds,onClick={draft=draft.copy(sportsRefreshSeconds=seconds)},label={Text(if(seconds<60) "${seconds}s" else "${seconds/60}m",fontSize=11.sp)})
            } }
            Text("Quiet scoreboards refresh at least every 3 minutes. Weather refreshes every 15 minutes.",color=Muted,fontSize=11.sp)
            SettingToggle("Sports ticker on weather screen",draft.tickerEnabled) {draft=draft.copy(tickerEnabled=it)}
            SettingToggle("Weather ticker on sports screen",draft.weatherTickerEnabled) {draft=draft.copy(weatherTickerEnabled=it)}
            Text("Stock market",color=Mint,fontSize=13.sp)
            OutlinedTextField(value=featured,onValueChange={featured=it},label={Text("Featured ticker (default HD)")},singleLine=true,modifier=Modifier.fillMaxWidth(),isError=normalizeStockSymbol(featured)==null)
            Text("Home Depot always stays visible, including when you feature another company.",color=Muted,fontSize=11.sp)
            OutlinedTextField(value=watchlist,onValueChange={watchlist=it},label={Text("Watchlist symbols, separated by commas")},modifier=Modifier.fillMaxWidth(),minLines=2)
            Text("Up to 50 symbols; e.g. AAPL, MSFT, BRK.B. Custom symbols use the Other category.",color=Muted,fontSize=11.sp)
            StockCategory.entries.chunked(3).forEach { categories ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { categories.forEach { category ->
                    FilterChip(selected=category in draft.stocks.categories,onClick={draft=draft.copy(stocks=draft.stocks.copy(categories=if(category in draft.stocks.categories) draft.stocks.categories-category else draft.stocks.categories+category))},label={Text(category.label,fontSize=10.sp)})
                } }
            }
            Text("Default chart range",color=Mint,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { ChartRange.entries.forEach { range ->
                FilterChip(selected=draft.stocks.range==range,onClick={draft=draft.copy(stocks=draft.stocks.copy(range=range))},label={Text(range.label,fontSize=11.sp)})
            } }
            SettingToggle("Stock ticker",draft.stocks.tickerEnabled) {draft=draft.copy(stocks=draft.stocks.copy(tickerEnabled=it))}
            SettingToggle("Rotate stock cards on each stock-screen visit",draft.stocks.rotate) {draft=draft.copy(stocks=draft.stocks.copy(rotate=it))}
            Text("Stock refresh (provider limits may lengthen this)",color=Mint,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { STOCK_REFRESH_INTERVALS.forEach { seconds ->
                FilterChip(selected=draft.stocks.refreshSeconds==seconds,onClick={draft=draft.copy(stocks=draft.stocks.copy(refreshSeconds=seconds))},label={Text("${seconds/60}m",fontSize=11.sp)})
            } }
            Text("Closed or unknown sessions refresh every 15–30 minutes. Failed requests back off automatically.",color=Muted,fontSize=11.sp)
            MarketIndex.entries.forEach { index ->
                SettingToggle(index.label,index in draft.stocks.indices) { checked -> draft=draft.copy(stocks=draft.stocks.copy(indices=if(checked) draft.stocks.indices+index else draft.stocks.indices-index)) }
            }
            OutlinedTextField(value=proxy,onValueChange={proxy=it},label={Text("Market-data proxy HTTPS URL")},singleLine=true,modifier=Modifier.fillMaxWidth(),isError=proxy.isNotBlank() && !validStockProxy(proxy.trim()))
            Text("Use your own proxy's HTTPS URL. Alpaca's API URL is an upstream service, not a proxy. Provider credentials stay on your server.",color=Muted,fontSize=11.sp)
            if(!stocksValid) Text("Check the featured symbol, watchlist (maximum 50), and HTTPS URL without credentials or query parameters.",color=MaterialTheme.colorScheme.error,fontSize=11.sp)
            SettingToggle("Keep screen awake",draft.keepAwake) {draft=draft.copy(keepAwake=it)}
        } },
        confirmButton={TextButton(enabled=stocksValid,onClick={onSave(draft.copy(stocks=draft.stocks.copy(featured=featured,watchlist=parsedWatchlist,proxyUrl=proxy).normalized()));onDismiss()}) {Text("Save settings")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Cancel")}})
}
@Composable private fun LeagueSelection(selected: Set<League>, onChange: (Set<League>) -> Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { League.entries.forEach { league ->
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text(league.name,fontSize=10.sp)
            Checkbox(checked=league in selected,onCheckedChange={ checked -> onChange(if(checked) selected+league else selected-league) })
        }
    } }
}
@Composable private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=13.sp,modifier=Modifier.weight(1f)); Switch(checked=checked,onCheckedChange=onChange)
    }
}
