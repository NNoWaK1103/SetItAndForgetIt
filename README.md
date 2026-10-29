# OpenTicker

A native Kotlin / Jetpack Compose sports and weather dashboard for an always-on landscape Android tablet. Android 8.0 (API 26) or later; no Android Studio, API key, account, backend, Firebase, or location permission required.

## Install an APK

1. Open [Android APK builds](https://github.com/NNoWaK1103/SetItAndForgetIt/actions/workflows/android-apk.yml).
2. Choose the latest **successful** run and download **OpenTicker-debug-apk** under Artifacts (GitHub sign-in may be required).
3. Extract the ZIP, copy `app-debug.apk` to the tablet, and open it. Allow installation from that browser/file manager when Android asks.
4. Open **OpenTicker**, tap **Settings**, search a city or ZIP, select a result, and save. Default: Hatboro, Pennsylvania.

This is a debug-signed APK for personal installation. CI explicitly caches the debug signing key to allow future APK updates while that cache is available; if the cache expires and Android rejects an update due to a different signing key, uninstall the old debug build first (this clears preferences). A release signing key is intentionally not stored in this repository.

## Dashboard

- Fullscreen slideshow: weather for 10 seconds, sports for 10 seconds, repeating while visible, with a 500 ms crossfade. Midnight-blue weather and charcoal sports backgrounds, vector weather icons, team logos with abbreviation fallbacks. The former split-screen layout has been removed.
- Sports opens on **ALL**, with NFL, NCAA FBS, NBA, MLB, NHL sections in a responsive scoreboard grid. Leagues with live games appear first; live games lead each section. Tap a league to filter, or ALL to restore all enabled sections. Scores, game clock/period, final, upcoming, postponed/delayed and canceled states remain available. The scoreboard requests today's date in US Eastern time. Upcoming game times and the header clock use the tablet's timezone.
- Current weather, feels-like, daily high/low, next 12 hourly entries, seven days, humidity, wind, daily maximum UV, current precipitation, sunrise/sunset. Forecast timestamps use the selected location's timezone.
- A stationary 60 dp ticker below the slideshow: **sports scores on the weather slide**, **current weather and hourly forecasts on the sports slide**. Only its contents crossfade. Text layout is cached; scrolling updates drawing without recomposing or measuring text each frame. Each wire retains its scroll position between slides and on data updates.
- Header **Weather** / **Sports** buttons switch manually. **Pause** / **Resume** controls automatic switching. Manual selections receive a full interval. Pausing or backgrounding preserves the current slide and remaining time; returning to the foreground resumes it. League filters and dashboard scroll positions survive slide changes.
- DataStore saves city/coordinates, °F/°C, enabled leagues, sports refresh interval (30/60/120/300 seconds), slideshow interval (5/10/15/30/60 seconds), playback pause, startup slide, separate sports/weather ticker toggles, animations and screen-awake options. Startup defaults to weather; changing the startup preference applies on the next process launch. Existing location, units, leagues and sports ticker preferences are preserved on upgrade.
- Turning animations off switches slides immediately and makes tickers static, with manual horizontal scrolling. Disabled tickers show a quiet status line while preserving the stationary bottom container.
- Immersive landscape display; swipe inward from a system edge to reveal system controls. Settings is always available in the header. Screen-awake applies only while the app is visible.

## Data and refresh behavior

ESPN's public scoreboard endpoints are **unofficial and unsupported**, and may change or be unavailable. `SportsRepository` isolates the provider. College football requests the FBS group (`80`) and a large event limit. Missing scores/statuses are displayed as unavailable; there are no bundled demo scores or generated weather values.

Open-Meteo supplies weather and geocoding without a key. Forecast requests use Fahrenheit/mph/inches; Celsius is converted locally. Open-Meteo data is [CC BY 4.0](https://open-meteo.com/en/licence); see the [forecast](https://open-meteo.com/en/docs) and [geocoding](https://open-meteo.com/en/docs/geocoding-api) documentation and free-service usage terms. Some ZIPs may return no matches; try the city name.

Live sports poll at the selected interval. Quiet scoreboards poll at `max(180 seconds, selected interval)`. Weather refreshes every 15 minutes, with a manual Refresh button. Failed sports requests retry no faster than the selected interval; failed weather requests have a 60-second minimum retry gap. Valid responses remain visible in memory on failure, with warnings and successful-update timestamps. Data is not persisted across process restarts; settings are persisted. Offline polling stops until Android reports a validated network. Requests have timeouts, cancellation, and a bounded connection retry. A mutex prevents overlapping refresh batches. Polling stops when the activity leaves the foreground; no background service or battery exemption is used.

`SlideshowController` owns a single coroutine and uses a monotonic clock. Foreground changes, preferences and manual selections are serialized through one event channel. It never calls a repository. Sports and weather remain in the shared ViewModel; a slide transition neither clears data nor resets polling. Backgrounding removes ticker animations, crossfades and UI clocks from composition. All jobs belong to the activity/ViewModel lifecycle.

## Build without Android Studio

Pushes to `main`, pull requests and manual workflow dispatch run `.github/workflows/android-apk.yml`. It installs JDK 17 and the Android command-line SDK, executes unit tests, Android lint and the debug build, and uploads the APK only after all checks succeed. Reports are uploaded even after failures. APK artifacts are retained for 90 days; rerun the workflow to regenerate one.

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

Physical Meswao B3 rendering, Wi-Fi interruption recovery, prolonged operation, and APK installation must also be checked on the target tablet; JVM tests and CI compilation cannot certify device performance.
