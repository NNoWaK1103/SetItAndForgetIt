# OpenTicker

## Integrated 1.5.1 source

This source combines the reliability fixes, hands-free dashboard browsing, private stock Worker, Philadelphia / Birds Eye design, same-day empty-sports handling, and completed-day stock-price fallback. Both the standard OpenTicker build and isolated GUI Preview use the updated interface. The standard app retains package `com.openticker.app`; the GUI Preview retains `com.openticker.app.figmapreview`. Existing explicitly saved settings remain respected. Fresh installs default to Philadelphia and sports-first playback.

CI tests, lints and builds both variants, publishing separate `OpenTicker-debug-apk` and `OpenTicker-gui-preview-apk` artifacts. Install only a matching package/signing identity over an existing app, and do not uninstall to bypass a signature mismatch. The stock Worker requires a separate, explicit deployment; pushing or merging this repository does not deploy it or configure real credentials. Local fixture tests do not verify live quotes or tablet rendering.

The dated preview sections and validation records below describe earlier deliveries. Their no-push/no-merge statements and APK hashes are historical, not the publication status or checksum of this integrated source. See [integration validation](docs/INTEGRATION_VALIDATION.md) for the current checks.

## Philadelphia / Birds Eye preview 1.5.1

The GUI preview now restores the reference's Philadelphia bird mark and BIRDS EYE branding, actual Barlow Condensed font, Eastern clock, separate sport tabs, Philly-first real games, and closer weather/market layouts. Both bottom wires are unchanged. Confirmed empty sports are hidden safely while unknown/loading/error feeds remain visible and polling continues.

The update keeps package `com.openticker.app.figmapreview`, advances to versionCode 11 and retains saved settings. Fresh installs use Philadelphia and sports-first playback. See [Philadelphia preview validation and installation notes](docs/PHILADELPHIA_PREVIEW.md). The original 1.4.2 stock-proxy package is preserved separately.


## GUI Preview 1.5.0

This standalone preview adapts the user's published Figma dashboard at https://preset-elude-78075939.figma.site to the existing native tablet app. It adds a coordinated near-black/teal palette, rounded scoreboard panels, condensed display typography, weather panels, orange market chart and a stationary chevron-style information wire. The app keeps its OpenTicker name and its actual data feeds; the prototype's sample scores, stock prices, allocation percentages and unimplemented navigation are not imported.

Build type `dashboardPreview` now produces **OpenTicker GUI Preview**, package `com.openticker.app.figmapreview`, version `1.5.0-figma-preview`. It installs alongside both production OpenTicker and the earlier Dashboard Preview, with separate settings and storage. Configure its location and optional private stock proxy separately. Do not uninstall your existing app or assume its settings transfer to this preview.

The original 1.4.2 private-token/proxy source package is retained separately and unchanged. This source is a distinct GUI preview derivative; no GitHub merge, push, server deployment or live account changes were made. See [GUI preview validation](docs/FIGMA_GUI_PREVIEW.md) for checks and remaining device validation.


A native Kotlin / Jetpack Compose weather, sports and stock-market dashboard for an always-on landscape Android tablet. Android 8.0 (API 26) or later. Weather and sports need no API key, account or backend. Stocks use an optional operator-configured HTTPS market-data proxy; private provider keys stay on that server. No Android Studio, Firebase or location permission required.

The stock proxy defaults to **Alpaca / IEX**, with explicit single-exchange coverage labels. SIP and 15-minute delayed SIP are configurable on the server. Alpaca's upstream API URL is separate from your own proxy URL; see [Alpaca setup](docs/STOCK_DATA.md).

For free-tier serverless hosting, use the included [Cloudflare Worker deployment guide](server/cloudflare/README.md). It provides mandatory private-token authentication, persistent shared caching, provider/request limits, quote/history/index endpoints and platform secrets. Use OpenTicker 1.4.2+ and enter a separate Worker access token in Settings; Alpaca keys never go in the app. The APK works independently and shows **Stock data not configured** until a proxy URL is saved. Watchlists support up to 500 symbols, requested in batches of 50.

## Install an APK

1. Open [Android APK builds](https://github.com/NNoWaK1103/SetItAndForgetIt/actions/workflows/android-apk.yml).
2. Choose the latest **successful** run and download **OpenTicker-debug-apk** under Artifacts (GitHub sign-in may be required).
3. Extract the ZIP, copy `app-debug.apk` to the tablet, and open it. Allow installation from that browser/file manager when Android asks.
4. Open **OpenTicker**, tap the **Settings** gear, search a city or ZIP, select a result, and save. Fresh-install default: Philadelphia, Pennsylvania.

This is a debug-signed APK for personal installation. CI explicitly caches the debug signing key to allow future APK updates while that cache is available; if the cache expires and Android rejects an update due to a different signing key, stop and rebuild with the original signing identity. **Do not uninstall the existing app to work around a signature mismatch**: that clears preferences and saved data. Local builds may use a different key from GitHub Actions; compare certificates before installing. A release signing key is intentionally not stored in this repository.

## Private stock connection (1.4.2)

The Cloudflare Worker now fails closed unless a private access token is configured. The app has masked token entry, URL-bound Android Keystore encryption, stock-only authorization headers, blocked redirects, and backup/transfer exclusions. Weather and sports are unchanged. Existing stock URLs need a saved token before requests resume. The serverless guide includes a live access-control verification step before adding any provider keys. No cloud deployment or live credentials are included in the source/APK.

## Dashboard

### Hands-free completion update (1.4.1)

- Stocks now browse continuously even if Stocks is the only enabled screen. Viewport-sized watchlist pages and the featured overview/price-history panel advance after ten seconds of accumulated visible time. Previous/Next, per-screen Pause, global Pause and dialog holds work independently. Manual paging still works when automatic stock browsing is disabled.
- Featured quote metrics no longer depend on scrolling to appear. Tap a company for a full cached-quote detail dialog; this does not create a new provider request. Choosing a chart range restarts the visible page interval.
- Sports cards show a two-line provider brief automatically: live situation and latest play when available, otherwise coverage and venue. Saved live statuses now say **SAVED • WAS LIVE** rather than appearing current.
- The header displays feed notices separately from network connectivity. Tap the status area for weather, each enabled sports feed, and market/index completeness, original update times and setup guidance. Delayed quotes can be healthy data; they are never relabeled real-time.
- Explicit compact line-height budgets keep the tablet typography readable without relying on the Material defaults. Stock capacity accounts for available height, row gaps and font scale. Real-device rendering still needs verification.

Hands-free means automatic browsing of the available information. Voice commands and microphone access are not implemented. Live market data still requires an operator-configured provider proxy.

### Professional display update (1.4.0)

For the isolated **OpenTicker Preview** APK and installation/data-safety details, see [preview notes](docs/PROFESSIONAL_DASHBOARD.md). The preview installs alongside the existing app and uses separate settings.

- Hands-free sports browsing cycles every game page, then the next nonempty group. Each league shows one or two readable cards based on available height. Custom groups larger than three cycle through column sets, so no league requires scrolling to discover. Viewing time is retained between slideshow visits, including 5-second intervals. The header Pause control, Settings, and open game details suspend automatic browsing; manual Next and group choices remain available. Automatic browsing can be disabled in Settings.
- Game cards now show provider-supplied overall records and rankings. Live football can show possession and down/distance; baseball can show count, outs and occupied bases. Tap a game for venue, broadcast, latest play and feed timestamp/provenance. Optional fields are omitted when ESPN does not supply them. No extra game-detail requests are made.
- A slim market wire stays visible on Weather and Sports, alongside the existing score/weather wire. It uses the same cached quotes and explicit feed/delay/stale timestamps as the Stocks screen. Unconfigured feeds show one clear setup message, never invented prices. Turn off “Market wire on every screen” to recover vertical space.
- Weather adds precipitation timing/probability, a six-hour temperature trend, daylight countdown and plain-language wind/UV context. Incomplete forecasts never claim it will stay dry. Fahrenheit uses mph/inches; Celsius uses km/h/mm. Open-Meteo current values are identified as model estimates.
- Compact displays automatically alternate **Now & outlook** and **Hourly & 7-day**, keeping all 12 hours and the full weekly view accessible without manual scrolling. Page progress and remaining viewing time survive slideshow transitions. Tap either page or pause pages independently. Weekly columns explicitly label high, low and precipitation chance.
- Settings and game-detail dialogs hold the main slideshow without changing the saved Pause preference or restarting network polling. Closing a dialog resumes the remaining screen time. Tablet-size headers use a compact layout.

The earlier behavior below describes the underlying feed/playback features; the automatic paging and persistent market wire above extend it.

- Fullscreen slideshow: **Sports → Stocks → Weather → Sports** on fresh installs, 15 seconds per screen by default, with a 500 ms crossfade. Existing saved orders remain intact. Settings can disable any screen and save an ordered playback list; disabling all screens stops playback and displays a settings prompt. Sports with confirmed current-day empty schedules are skipped without rewriting saved preferences. The dashboards share manual navigation, pause/resume and configurable intervals.
- Stocks prominently features **The Home Depot (HD, NYSE)**, price, dollar/percentage changes, previous close, session high/low, volume, freshness and timestamps in US Eastern time. Select actual-history ranges **1D / 1W / 1M / 3M / 1Y** (default 1D). A native Compose Canvas chart displays real historical points only; missing intraday data has an explicit message. Viewport-sized watchlist pages rotate continuously while visible, independently of the featured company; HD remains visible if a different stock is featured. Categories, ordered watchlist, chart, rotation, indices and refresh/ticker options persist in DataStore.
- S&P 500, Dow Jones and Nasdaq Composite cards show provider values or neutral unavailable states. The stock ticker shows HD first with colored gains/losses and labeled real-time, delayed or previous-close prices. No stock prices, company ranks or current Fortune 500 membership claims are fabricated. Fortune 500 is a revenue ranking, not an index. Stocks require a licensed data service: see [provider evaluation, reference proxy, API contract and setup](docs/STOCK_DATA.md).
- Sports defaults to **Group A: NFL / NCAAF / NHL**, displayed simultaneously in three equal-width, paged columns. **Group B: NBA / MLB** uses two equal-width columns. Each column has a locally drawn sport icon, league heading, last-update time, team logos, scores, status, period/clock, and empty/error states. Games sort live first, upcoming next, then finals with the latest start times first. The scoreboard requests today's date in US Eastern time. Upcoming game times and the header clock use the tablet's timezone.
- The small **NFL / NCAAF / NHL | NBA / MLB** selector switches groups manually without resetting the slideshow or starting network requests. Memberships and the active group are saved in DataStore and restored on launch. Settings can place any league in either group; groups may overlap or be empty. Custom groups of four or five cycle through sets of at most three columns to retain readable cards. Page positions and remaining visible time survive slide changes. Hands-free browsing rotates pages and nonempty groups unless disabled or paused.
- Current weather, feels-like, daily high/low, next 12 hourly entries, seven days, humidity, wind, daily maximum UV, current precipitation, sunrise/sunset. Forecast timestamps use the selected location's timezone.
- A stationary 60 dp ticker below the slideshow: **scores from the selected sports group on weather**, **weather and hourly forecasts on sports**, **selected stock prices on stocks**. Settings can include **all five leagues** in the sports ticker instead, including feeds disabled for the dashboard. Live scores lead upcoming games and recent finals. Only ticker contents crossfade. Text layout is cached; scrolling updates drawing without recomposing or measuring text each frame. Each wire retains its scroll position between slides and on data updates.
- Header buttons for **Weather / Sports / Stocks** screens switch manually, including screens disabled for automatic rotation. **Pause** / **Resume** controls automatic switching. Manual selections receive a full interval. Pausing or backgrounding preserves the current slide and remaining time; returning to the foreground resumes it. League filters and dashboard scroll positions survive slide changes.
- DataStore saves city/coordinates, °F/°C, enabled leagues, sports refresh interval (30/60/120/300 seconds), slideshow interval (5/10/15/20/30/60 seconds), playback pause, startup slide, separate sports/weather ticker toggles, animations and screen-awake options. Startup defaults to weather; changing the startup preference applies on the next process launch. Existing location, units, leagues and sports ticker preferences are preserved on upgrade.
- Turning animations off switches slides immediately and makes tickers static, with manual horizontal scrolling. Disabled tickers show a quiet status line while preserving the stationary bottom container.
- Settings remain open while Save is pending, retain the complete draft on a failed save, and close only after persistence succeeds. Cancel, Back and outside dismissal discard unsaved edits consistently, including slideshow interval changes.
- Immersive landscape display; swipe inward from a system edge to reveal system controls. Settings is always available in the header. Screen-awake applies only while the app is visible.

## Data and refresh behavior

ESPN's public scoreboard endpoints are **unofficial and unsupported**, and may change or be unavailable. `SportsRepository` isolates the provider. College football requests the FBS group (`80`) and a large event limit. Missing scores/statuses are displayed as unavailable; there are no bundled demo scores or generated weather values.

Open-Meteo supplies weather and geocoding without a key. Forecast requests use Fahrenheit/mph/inches; Celsius is converted locally. Open-Meteo data is [CC BY 4.0](https://open-meteo.com/en/licence); see the [forecast](https://open-meteo.com/en/docs) and [geocoding](https://open-meteo.com/en/docs/geocoding-api) documentation and free-service usage terms. Some ZIPs may return no matches; try the city name.

Live sports poll at the selected interval. Quiet scoreboards poll at `max(180 seconds, selected interval)`. Weather refreshes every 15 minutes, with a manual Refresh button. Failed sports requests retry no faster than the selected interval; failed weather requests have a 60-second minimum retry gap. Last-good weather, per-league scoreboards, stock quotes and chart history are saved privately on the tablet and restored after process restarts, including offline launches. Restored data keeps its original timestamps and is explicitly marked saved/stale until refreshed. Weather caches are separated by location and stock caches by proxy URL. Invalid cache files are ignored; failed cache writes do not interrupt live data. The header reports network connectivity separately from per-feed service, connection and response errors. Offline polling stops until Android reports a validated network. Requests have timeouts, cancellation, and a bounded connection retry. A mutex prevents overlapping refresh batches. Polling stops when the activity leaves the foreground; no background service or battery exemption is used.

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

On Linux/macOS use `./gradlew`. Output: `app/build/outputs/apk/debug/app-debug.apk`. No build success is implied by the presence of this source. GitHub Actions records remote builds; local preview validation is documented separately.

## Source layout and tests

`com.openticker.app` contains `data/model`, `data/remote`, `data/repository`, `settings`, `viewmodel`, `util`, and `ui/{theme,components,screens}`. Immutable dashboard state is published by StateFlow and collected with lifecycle awareness. Network and parsing work stays outside Composables. OkHttp, kotlinx.serialization, Coil and AndroidX are the only runtime infrastructure dependencies.

Offline JUnit fixtures cover ESPN parsing across all leagues, status mapping, null/missing fields, invalid response rejection, weather parsing and mismatched arrays, WMO condition mapping, both temperature conversions, geocoding, league filtering, game ordering and ticker text. Fixture scores exist only under `src/test`.

Virtual-time coroutine tests cover slideshow boundaries and repeated cycles, background and user pause/resume with remaining time, manual selection, interval changes, startup preferences, duplicate foreground events and ticker selection. Persistence tests write and reopen actual DataStore files, verify legacy preferences, and check invalid-value defaults. Weather ticker tests verify actual fixture values, temperature units, missing data and expired forecasts. Tests do not require live network access.

Stock tests add three-screen order, custom order and disabling each screen, quote/history parsing, daily changes, caches, stale retention, backoff, session status, ticker labels, pagination and preferences. Node tests cover Alpaca and Massive adapters, authenticated read-only requests, quota/reset handling, historical pagination, feed labels, retained values and the HTTP contract. Persistence tests use real JVM filesystem storage with DataStore 1.3.0's public Preferences serializer, avoiding the mock Android runtime's legacy rename path on Windows.

Sports-group tests cover defaults, custom/overlapping/empty groups, active-group ticker filtering, all-five ticker mode, ordering recent finals, stable polling configuration across group changes, atomic active-group saves, and persistence after reopening DataStore. A virtual-time regression test verifies that changing groups and memberships mid-slide preserves both configured deadlines. See [implementation plan](docs/IMPLEMENTATION_PLAN.md).

Physical Meswao B3 rendering, Wi-Fi interruption recovery, prolonged operation, and APK installation must also be checked on the target tablet; JVM tests and CI compilation cannot certify device performance.
