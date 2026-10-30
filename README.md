# OpenTicker

A native Kotlin / Jetpack Compose weather, sports and stock-market dashboard for an always-on landscape Android tablet. Android 8.0 (API 26) or later. Weather and sports need no API key, account or backend. Stocks use an optional operator-configured HTTPS market-data proxy; private provider keys stay on that server. No Android Studio, Firebase or location permission required.

The stock proxy defaults to **Alpaca / IEX**, with explicit single-exchange coverage labels. SIP and 15-minute delayed SIP are configurable on the server. Alpaca's upstream API URL is separate from your own proxy URL; see [Alpaca setup](docs/STOCK_DATA.md).

For free-tier serverless hosting, use the included [Cloudflare Worker deployment guide](server/cloudflare/README.md). It provides persistent shared caching, provider rate limits, quote/history/index endpoints and platform secrets. The APK works independently and shows **Stock data not configured** until a proxy URL is saved. Watchlists support up to 500 symbols, requested in batches of 50.

## Install an APK

1. Open [Android APK builds](https://github.com/NNoWaK1103/SetItAndForgetIt/actions/workflows/android-apk.yml).
2. Choose the latest **successful** run and download **OpenTicker-debug-apk** under Artifacts (GitHub sign-in may be required).
3. Extract the ZIP, copy `app-debug.apk` to the tablet, and open it. Allow installation from that browser/file manager when Android asks.
4. Open **OpenTicker**, tap **Settings**, search a city or ZIP, select a result, and save. Default: Hatboro, Pennsylvania.

This is a debug-signed APK for personal installation. CI explicitly caches the debug signing key to allow future APK updates while that cache is available; if the cache expires and Android rejects an update due to a different signing key, uninstall the old debug build first (this clears preferences). A release signing key is intentionally not stored in this repository.

## Dashboard

- Fullscreen slideshow: **Weather → Sports → Stocks → Weather**, 15 seconds per screen by default, with a 500 ms crossfade. Settings can disable any screen and save an ordered playback list; disabling all screens stops playback and displays a settings prompt. Midnight-blue weather, charcoal sports and navy stock dashboards share manual navigation, pause/resume and configurable intervals.
- Stocks prominently features **The Home Depot (HD, NYSE)**, price, dollar/percentage changes, previous close, session high/low, volume, freshness and timestamps in US Eastern time. Select actual-history ranges **1D / 1W / 1M / 3M / 1Y** (default 1D). A native Compose Canvas chart displays real historical points only; missing intraday data has an explicit message. Eight watchlist cards rotate on successive stock-screen visits, independently of the featured card; HD remains visible if a different stock is featured. Categories, ordered watchlist, chart, rotation, indices and refresh/ticker options persist in DataStore.
- S&P 500, Dow Jones and Nasdaq Composite cards show provider values or neutral unavailable states. The stock ticker shows HD first with colored gains/losses and labeled real-time, delayed or previous-close prices. No stock prices, company ranks or current Fortune 500 membership claims are fabricated. Fortune 500 is a revenue ranking, not an index. Stocks require a licensed data service: see [provider evaluation, reference proxy, API contract and setup](docs/STOCK_DATA.md).
- Sports defaults to **Group A: NFL / NCAAF / NHL**, displayed simultaneously in three equal-width, independently scrolling columns. **Group B: NBA / MLB** uses two equal-width columns. Each column has a locally drawn sport icon, league heading, last-update time, team logos, scores, status, period/clock, and empty/error states. Games sort live first, upcoming next, then finals with the latest start times first. The scoreboard requests today's date in US Eastern time. Upcoming game times and the header clock use the tablet's timezone.
- The small **NFL / NCAAF / NHL | NBA / MLB** selector switches groups manually without resetting the slideshow or starting network requests. Memberships and the active group are saved in DataStore and restored on launch. Settings can place any league in either group; groups may overlap or be empty. Custom groups of four or five use an equal-width panel grid to retain readable cards. Scroll positions are retained per group and across slide changes. Groups do not rotate automatically.
- Current weather, feels-like, daily high/low, next 12 hourly entries, seven days, humidity, wind, daily maximum UV, current precipitation, sunrise/sunset. Forecast timestamps use the selected location's timezone.
- A stationary 60 dp ticker below the slideshow: **scores from the selected sports group on weather**, **weather and hourly forecasts on sports**, **selected stock prices on stocks**. Settings can include **all five leagues** in the sports ticker instead, including feeds disabled for the dashboard. Live scores lead upcoming games and recent finals. Only ticker contents crossfade. Text layout is cached; scrolling updates drawing without recomposing or measuring text each frame. Each wire retains its scroll position between slides and on data updates.
- Header buttons for **Weather / Sports / Stocks** screens switch manually, including screens disabled for automatic rotation. **Pause** / **Resume** controls automatic switching. Manual selections receive a full interval. Pausing or backgrounding preserves the current slide and remaining time; returning to the foreground resumes it. League filters and dashboard scroll positions survive slide changes.
- DataStore saves city/coordinates, °F/°C, enabled leagues, sports refresh interval (30/60/120/300 seconds), slideshow interval (5/10/15/20/30/60 seconds), playback pause, startup slide, separate sports/weather ticker toggles, animations and screen-awake options. Startup defaults to weather; changing the startup preference applies on the next process launch. Existing location, units, leagues and sports ticker preferences are preserved on upgrade.
- Turning animations off switches slides immediately and makes tickers static, with manual horizontal scrolling. Disabled tickers show a quiet status line while preserving the stationary bottom container.
- Immersive landscape display; swipe inward from a system edge to reveal system controls. Settings is always available in the header. Screen-awake applies only while the app is visible.

## Data and refresh behavior

ESPN's public scoreboard endpoints are **unofficial and unsupported**, and may change or be unavailable. `SportsRepository` isolates the provider. College football requests the FBS group (`80`) and a large event limit. Missing scores/statuses are displayed as unavailable; there are no bundled demo scores or generated weather values.

Open-Meteo supplies weather and geocoding without a key. Forecast requests use Fahrenheit/mph/inches; Celsius is converted locally. Open-Meteo data is [CC BY 4.0](https://open-meteo.com/en/licence); see the [forecast](https://open-meteo.com/en/docs) and [geocoding](https://open-meteo.com/en/docs/geocoding-api) documentation and free-service usage terms. Some ZIPs may return no matches; try the city name.

Live sports poll at the selected interval. Quiet scoreboards poll at `max(180 seconds, selected interval)`. Weather refreshes every 15 minutes, with a manual Refresh button. Failed sports requests retry no faster than the selected interval; failed weather requests have a 60-second minimum retry gap. Valid responses remain visible in memory on failure, with warnings and successful-update timestamps. Data is not persisted across process restarts; settings are persisted. Offline polling stops until Android reports a validated network. Requests have timeouts, cancellation, and a bounded connection retry. A mutex prevents overlapping refresh batches. Polling stops when the activity leaves the foreground; no background service or battery exemption is used.

Both groups' enabled feeds stay on the existing polling schedule, so switching groups uses shared cached data immediately. Only changes to enabled feeds, the all-five ticker requirement, refresh interval, connectivity, foreground state or weather location can change polling configuration. The active group and group memberships are excluded. The all-five ticker option polls all five while the sports ticker is enabled; turning that ticker off returns to the enabled-feed set.

`SlideshowController` owns a single coroutine and uses a monotonic clock over a configurable ordered list of enabled screens. Foreground changes, preferences and manual selections are serialized through one event channel. It never calls a repository. Sports/weather remain in the existing ViewModel; stocks has an independent ViewModel and repository cache. A slide transition neither clears data nor resets polling. Backgrounding removes ticker animations, crossfades and UI clocks from composition. All jobs belong to the activity/ViewModel lifecycle.

## Build without Android Studio

Pushes to `main`, pull requests and manual workflow dispatch run `.github/workflows/android-apk.yml`. It installs JDK 17, Node 24 and the Android command-line SDK, runs offline Node/Worker market-proxy tests, validates the Worker bundle without deployment, and runs Android unit tests, lint and the debug build, and uploads the APK only after all checks succeed. Reports are uploaded even after failures. APK artifacts are retained for 90 days; rerun the workflow to regenerate one. The build summary includes the APK SHA-256.

Pinned toolchain: AGP 8.13.2, Gradle 8.13 (official checked-in wrapper with distribution checksum), Kotlin/Compose compiler 2.2.21, compile/target SDK 36, JDK 17. API 36 is the stable platform supported by this AGP line. See [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

Optional local command-line build, with JDK 17 and Android SDK installed:

```powershell
$env:ANDROID_HOME = 'C:\Android\Sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On Linux/macOS use `./gradlew`. Output: `app/build/outputs/apk/debug/app-debug.apk`. No build success is implied by the presence of this source; the linked Actions run is the build record.

## Source layout and tests

`com.openticker.app` contains `data/model`, `data/remote`, `data/repository`, `settings`, `viewmodel`, `util`, and `ui/{theme,components,screens}`. Immutable dashboard state is published by StateFlow and collected with lifecycle awareness. Network and parsing work stays outside Composables. OkHttp, kotlinx.serialization, Coil and AndroidX are the only runtime infrastructure dependencies.

Offline JUnit fixtures cover ESPN parsing across all leagues, status mapping, null/missing fields, invalid response rejection, weather parsing and mismatched arrays, WMO condition mapping, both temperature conversions, geocoding, league filtering, game ordering and ticker text. Fixture scores exist only under `src/test`.

Virtual-time coroutine tests cover slideshow boundaries and repeated cycles, background and user pause/resume with remaining time, manual selection, interval changes, startup preferences, duplicate foreground events and ticker selection. Persistence tests write and reopen actual DataStore files, verify legacy preferences, and check invalid-value defaults. Weather ticker tests verify actual fixture values, temperature units, missing data and expired forecasts. Tests do not require live network access.

Stock tests add three-screen order, custom order and disabling each screen, quote/history parsing, daily changes, caches, stale retention, backoff, session status, ticker labels, pagination and preferences. Node tests cover Alpaca and Massive adapters, authenticated read-only requests, quota/reset handling, historical pagination, feed labels, retained values and the HTTP contract. Persistence tests use real JVM filesystem storage with DataStore 1.3.0's public Preferences serializer, avoiding the mock Android runtime's legacy rename path on Windows.

Sports-group tests cover defaults, custom/overlapping/empty groups, active-group ticker filtering, all-five ticker mode, ordering recent finals, stable polling configuration across group changes, atomic active-group saves, and persistence after reopening DataStore. A virtual-time regression test verifies that changing groups and memberships mid-slide preserves both configured deadlines. See [implementation plan](docs/IMPLEMENTATION_PLAN.md).

Physical Meswao B3 rendering, Wi-Fi interruption recovery, prolonged operation, and APK installation must also be checked on the target tablet; JVM tests and CI compilation cannot certify device performance.
