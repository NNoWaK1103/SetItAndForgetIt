Historical initial 1.5.0 notes. The current revision is documented in PHILADELPHIA_PREVIEW.md.

# OpenTicker GUI Preview 1.5.0

Prepared November 8, 2026 from recovered OpenTicker 1.4.2 source. This is a separate GUI preview derivative, not a merge into the production project.

## Reference and adaptation

Published visual reference: https://preset-elude-78075939.figma.site

- Shared near-black teal palette, thin rounded borders and locally bundled condensed typography.
- Top navigation, explicit device-time clock, pause/resume, data-feed status and settings remain available.
- Sports uses rounded league panels and live-status game cards with provider records, scores, possession, brief and cached details. Configurable league groups and automatic game browsing remain native app controls.
- Weather uses large current-condition panels, a compact metrics row, outlook/daylight cards and hourly/weekly panels. The existing two-page mode remains for compact tablet viewports; full 12-hour and seven-day forecasts remain accessible.
- Stocks uses a wider featured panel, orange symbol tile and real-history area chart, watchlist panels and Market Pulse. Actual feed timestamps and coverage remain visible. Short layouts keep overview/history paging and scroll fallbacks.
- Stationary chevron-style score/weather/market wires preserve existing scroll-position and animation behavior.
- OpenTicker branding and all real data are retained. No prototype prices, scores, portfolio weights, fake news, game outcomes or fabricated conditions are included.

## Final verification

- Final aggregate succeeded after review fixes: `testDebugUnitTest testDashboardPreviewUnitTest lintDebug lintDashboardPreview assembleDebug assembleDashboardPreview`.
- 146 Android JVM unit tests passed for debug, and the same 146 passed for GUI preview. Zero failures, errors or skips.
- Both Android lint tasks passed with zero errors and two warnings: GradleDependency and DiscouragedApi.
- 41 Node/provider/Worker fixture tests passed. Additional workerd + SQLite integration test passed, exercising authentication, shared cache and rejected redirects. The combined Worker test command includes 20 overlapping fixture tests plus that one runtime test.
- Wrangler bundle dry run passed. No Worker was deployed and no live credentials or accounts were used.
- Independent static review checked state/paging, real-data labeling, layout constraints and proxy preservation. Device-time labeling and short-stock-layout clipping findings were fixed and rechecked; the complete Android aggregate was rerun afterward.
- All baseline data, repository, settings, proxy/security, viewmodel and browsing utility files are byte-identical to the 1.4.2 source. No baseline files are missing.
- Preview APK manifest and signature verified: `com.openticker.app.figmapreview`, versionCode 10, `1.5.0-figma-preview`, minimum Android 8.0/API26, target API36, APK v2 signature valid.
- APK SHA-256: `bd88930521cda4b68f7663c29732d807252a5f8535cbe36e01f6cd11e7194b8b`.

## Not yet verified

No physical Android device is connected. No installed emulator or KVM device is available. This build has not been run for rendered screenshot comparison, device navigation, large-font accessibility, lifecycle interruption, prolonged operation or installation on the user's tablet. Static review and JVM tests do not certify those runtime checks. No pixel-perfect reproduction is claimed.

## Installation and safety

Install **OpenTicker GUI Preview** alongside the current app. It uses separate package identity, preferences and private storage from production OpenTicker and the earlier Dashboard Preview. Configure the preview's location and optional private stock connection separately; existing settings are not copied. Do not uninstall the current app to install this preview. This APK is locally debug-signed and is not a production update.

The original 1.4.2 source archive is preserved unchanged. No remote commit, push, merge, deployment, account change or device setting was made. The source archive excludes build outputs, caches, local SDK paths and private signing material. The bundled DejaVu font includes its redistribution notice in app assets.
