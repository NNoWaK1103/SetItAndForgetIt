# OpenTicker recovery record — October 30, 2026

## Recovered checkpoint

The repository was preserved and extended in place. At the start of recovery:

- GitHub `main` ended at `abc46e9` (October 29). Its APK run `40866874607` and tablet-preview run `40866874571` both succeeded. Earlier failed runs were superseded by those successful runs.
- Local `main` was two commits ahead: `1290a5f` implemented the Home Depot stock dashboard, three-screen slideshow, sports groups, stock settings/caches/tests, reference market proxy and documentation; `2f50514` added the Alpaca adapter and coverage labels. Neither commit had been pushed.
- Twelve tracked files had uncommitted changes covering navigation, interval selection/persistence, playback controls, stock polling, tests, version 1.3.1 and preview CI. `scripts/verify-playback.mjs` was untracked. All this work was retained.
- The last saved emulator report, `downloads/playback-verification/results.json`, said `passed: false` and `Stocks tab must be selected`; its verification arrays were empty. The durable stopping point was unfinished playback verification and delivery, after the stock implementation had been committed locally. The precise instant of shutdown cannot be established from repository evidence.

## Completed during recovery

- Set the default and invalid-value fallback to 15 seconds: Weather → Sports → Stocks → Weather. Existing explicitly saved intervals remain intact; select 15s in Settings on an installation previously set to 10s.
- Kept the recovered manual-navigation and atomic playback-preference fixes, including manual access to screens excluded from automatic rotation and protection against tab presses lost at a timer deadline.
- Added repeated default-cycle coverage alongside configurable 10/15/20-second, pause/background, persistence and deadline regression cases.
- Finished the real-APK playback verifier, including fresh-install defaults, a complete 15-second cycle, manual tabs, persisted intervals, disabled automatic screens and crash checks. It recognizes both selected tabs and checked Material chips.
- Updated preview CI to run playback checks and trigger for application/build changes. Capture accepts real weather loading/error states and records 50 seconds to include the complete default cycle.
- Made stock setup guidance visible while offline and synchronized the documentation with the recovered behavior.

## Validation and delivery

The first local `testDebugUnitTest`, `lintDebug` and `assembleDebug` run passed: 81 Android unit tests, zero failures/errors. Lint has no errors; existing toolchain-update, fixed-orientation and backup-configuration warnings remain. All 21 existing offline Node market-data tests passed. Real-APK checks then passed the default full 15-second cycle, all manual tabs, 10/15/20-second interval persistence after process restarts, manual access to disabled automatic screens, and crash checks on an Android API 37 emulator.

The debug APK is version 1.3.1 (code 6), Android 8.0/API 26 or later, at `app/build/outputs/apk/debug/app-debug.apk`. Debug signing identities can differ between local and CI builds; use the same distribution source when updating an existing installation.

## Remaining operator/device work

Live stock prices need the operator's deployed HTTPS proxy URL and server-side provider credentials. The user confirmed that no proxy is deployed and requested a serverless option. The [Cloudflare Worker and deployment guide](../server/cloudflare/README.md) now provide that option, with 13 offline Worker tests, Wrangler bundle validation and real local-runtime binding checks. The final Android build passed all 82 unit tests, lint and APK assembly; all 34 existing and Worker backend tests passed. Android also supports a 500-symbol watchlist through bounded batches of 50. Prices stay explicitly unavailable until configured. Alpaca IEX coverage is labeled; optional Massive credentials supply actual cash-index EOD data. No quotes are fabricated. Proxy deployment is separate from APK delivery and does not block it.

Physical-tablet installation and prolonged operation still require validation on the target device. Emulator/JVM checks cannot certify that hardware.
