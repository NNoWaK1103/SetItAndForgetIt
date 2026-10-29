# OpenTicker

A native Kotlin / Jetpack Compose sports and weather dashboard for an always-on landscape Android tablet. Android 8.0 (API 26) or later; no Android Studio, API key, account, backend, Firebase, or location permission required.

## Install an APK

1. Open [Android APK builds](https://github.com/NNoWaK1103/SetItAndForgetIt/actions/workflows/android-apk.yml).
2. Choose the latest **successful** run and download **OpenTicker-debug-apk** under Artifacts (GitHub sign-in may be required).
3. Extract the ZIP, copy `app-debug.apk` to the tablet, and open it. Allow installation from that browser/file manager when Android asks.
4. Open **OpenTicker**, tap **Settings**, search a city or ZIP, select a result, and save. Default: Hatboro, Pennsylvania.

This is a debug-signed APK for personal installation. CI explicitly caches the debug signing key to allow future APK updates while that cache is available; if the cache expires and Android rejects an update due to a different signing key, uninstall the old debug build first (this clears preferences). A release signing key is intentionally not stored in this repository.

## Dashboard

- Equal sports/weather panels, charcoal and midnight backgrounds, vector weather icons, team logos with abbreviation fallbacks.
- NFL, NCAA FBS, NBA, MLB, NHL tabs; live games first; scores, game clock/period, final, upcoming, postponed/delayed and canceled states. The scoreboard requests today's date in US Eastern time. Upcoming game times and the header clock use the tablet's timezone.
- Current weather, feels-like, daily high/low, next 12 hourly entries, seven days, humidity, wind, daily maximum UV, current precipitation, sunrise/sunset. Forecast timestamps use the selected location's timezone.
- Smooth looping score wire using Compose's marquee rendering. Toggle it off in Settings.
- Saved city/coordinates, °F/°C, enabled leagues, refresh interval (30/60/120/300 seconds), ticker and screen-awake options via DataStore.
- Immersive landscape display; swipe inward from a system edge to reveal system controls. Settings is always available in the header. Screen-awake applies only while the app is visible.

## Data and refresh behavior

ESPN's public scoreboard endpoints are **unofficial and unsupported**, and may change or be unavailable. `SportsRepository` isolates the provider. College football requests the FBS group (`80`) and a large event limit. Missing scores/statuses are displayed as unavailable; there are no bundled demo scores or generated weather values.

Open-Meteo supplies weather and geocoding without a key. Forecast requests use Fahrenheit/mph/inches; Celsius is converted locally. Open-Meteo data is [CC BY 4.0](https://open-meteo.com/en/licence); see the [forecast](https://open-meteo.com/en/docs) and [geocoding](https://open-meteo.com/en/docs/geocoding-api) documentation and free-service usage terms. Some ZIPs may return no matches; try the city name.

Live sports poll at the selected interval. Quiet scoreboards poll at `max(180 seconds, selected interval)`. Weather refreshes every 15 minutes, with a manual Refresh button. Failed sports requests retry no faster than the selected interval; failed weather requests have a 60-second minimum retry gap. Valid responses remain visible in memory on failure, with warnings and successful-update timestamps. Data is not persisted across process restarts; settings are persisted. Offline polling stops until Android reports a validated network. Requests have timeouts, cancellation, and a bounded connection retry. A mutex prevents overlapping refresh batches. Polling stops when the activity leaves the foreground; no background service or battery exemption is used.

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

Physical Meswao B3 rendering, Wi-Fi interruption recovery, prolonged operation, and APK installation must also be checked on the target tablet; JVM tests and CI compilation cannot certify device performance.
