# Professional dashboard preview

## Scope

Version 1.4.1 extends the first 1.4.0 preview (`a5c726a`) and builds on local reliability commit `85cea79`, which remains on its original branch. Remote main was verified at `0008c2d` before work began. This preview adds hands-free sports game/group paging, provider-supplied game context and details, compact weather pages and summaries, and an optional market wire on every screen. It does not configure or purchase a stock service, deploy a proxy, or publish/merge repository changes.

## Installation and data safety

The distributable uses a separate application ID, `com.openticker.app.dashboardpreview`, and launcher name **OpenTicker Preview**. It installs alongside OpenTicker, without replacing or deleting the existing installation. The current local signing certificate differs from the earlier reliability APK, so a regular same-package APK must not be used as an update to that earlier build.

The preview starts with its own preferences and cache. Select a city and other preferences in Settings. If a market-data proxy is already in use, enter that same HTTPS proxy URL in this preview; never put provider secrets into the app. No stock service is assumed to be configured. Do not uninstall the original app or clear its storage to install this preview.

Normal debug/release package identity is unchanged in source. To update the original installation, build the normal variant with the original signing identity. Do not copy signing secrets into this repository.

## Display and interaction

- Sports advances after ten seconds of accumulated visible viewing time, preserving the countdown across short slideshow visits. All game pages precede the next nonempty group. Groups with more than three leagues cycle column sets. Short leagues repeat while longer leagues finish. Page indexes safely adapt when scores arrive or disappear.
- The original manually selected group remains the saved preference and controls the score wire. Automatic browsing has its own view cursor and makes no settings writes or additional network calls.
- Tap a game for optional provider details and original feed status. Opening it or Settings holds the main slideshow; closing resumes the remaining interval. Global Pause also pauses sports and weather browsing. Feeds can still refresh while a dialog is open.
- Compact weather alternates Now & outlook with Hourly & 7-day. At normal Android font scale, the content budget targets a landscape 853 × 533dp display, including both wires. Weather and sports have independent page-pause controls.
- Market data remains entitlement-dependent: unavailable, delayed, previous-close and stale data keep their labels and original timestamps. The preview does not fabricate quotes or index levels.
- Screen-awake and automatic refresh work only while the app is foregrounded. There is no boot launcher, background service or battery exemption.

## Second iteration: hands-free coverage

- Stock browsing no longer depends on leaving and revisiting Stocks. The watchlist pages and featured overview/history panel alternate on visible-time clocks, including a Stocks-only setup. A full 500-symbol watchlist remains reachable; HD stays featured or pinned when another company is featured.
- Watchlist capacity uses a 144dp normal-font card budget plus 8dp row gaps, adjusted for font scale. Explicit line heights also cover weather and sports. This is a source-derived layout budget, not a screenshot/device certification.
- Stock quote details display cached values, exchange, freshness, timestamp and metrics without initiating a new data request. The dialog holds playback. Chart range changes retain normal repository caching and reset the page timer for reading.
- Sports game briefs appear without tapping. Saved scoreboards label prior live status as “SAVED • WAS LIVE.” Venue/broadcast/live-play text is only displayed when ESPN supplies it.
- The header's feed notices distinguish network connectivity from data service health. The detail panel includes selected index availability and staleness, original timestamps and configuration guidance. The panel also holds slideshow and page timers.
- Voice control is not implemented, and the app does not request microphone access. Hands-free refers to automatic information browsing. The live-stock provider/hosting setup remains separate; no active proxy, credentials, license entitlement or paid service is assumed.

## Device acceptance still required

This environment cannot certify actual tablet rendering, installation or prolonged performance. The retained emulator had no usable snapshot and no hardware acceleration; previous cold boots in this environment stalled/ANRed, so those failed attempts were not repeated for this preview.

On the tablet, check:

1. Install **OpenTicker Preview** alongside the existing app. Confirm both remain available.
2. First leave only Stocks enabled and verify watchlist and overview/history pages continue cycling. Then run slideshow at 5, 10 and 15 seconds, with animations on and off. After several visits, verify sports reaches all groups/pages and compact weather reaches its second page.
3. Open sports details, stock quote details, feed status and Settings near a transition. Verify the screen holds, Close/Back resumes it, and saved Pause is not changed. Repeat opening/dismissing; background/foreground the app.
4. Check long team names, dense college-football days, warning text, landscape layout, large font settings and the full hourly/weekly weather display.
5. Confirm offline starts retain prior values with saved/stale labels and original timestamps; restore Wi-Fi and verify fresh data clears them. Unconfigured stocks should show one setup message, not fabricated values.
6. Leave it running plugged in, noting temperature, screen brightness and screen-retention risk for the tablet. Keep brightness appropriate for a static dashboard.

## Build commands

From a JDK 17 / Android SDK environment:

```
./gradlew testDebugUnitTest lintDebug assembleDebug testDashboardPreviewUnitTest lintDashboardPreview assembleDashboardPreview
node --test server/*.test.mjs server/cloudflare/*.test.mjs
```

The isolated preview output is `app/build/outputs/apk/dashboardPreview/app-dashboardPreview.apk`. See `docs/evidence/professional-dashboard/` for the validation summary. No live market provider test is claimed without a configured proxy.
