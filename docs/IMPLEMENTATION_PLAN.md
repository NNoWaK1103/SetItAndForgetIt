# OpenTicker implementation plan: sports groups and stock dashboard

## Display contract

- Extend the slideshow to weather → sports → stocks → weather with a configurable ordered list of enabled screens. Keep the default 10-second dwell time, pause/resume controls, 500 ms crossfade, configurable interval and lifecycle cancellation.
- Replace the previous ALL-leagues sports grid with Group A (NFL, NCAAF, NHL) in three equal columns and Group B (NBA, MLB) in two equal columns. Preserve all five provider implementations.
- Give each league its own sport icon, heading, update timestamp and scrollable game list. Order games live, upcoming, then newest finals. Retain team logos, scores, clock/period and error/empty states.
- Put a compact group selector in the sports header. Restore the selected group on launch. Groups only change through explicit user selection.
- Let custom groups contain zero to five leagues, including overlapping memberships. Use equal-width panel grids for groups larger than three to avoid narrow, unreadable columns.

## State and persistence

- Store Group A and B memberships, active group, and all-five sports ticker preference in the existing DataStore. Preserve legacy location, units, slideshow, enabled feeds and ticker preferences.
- Update the active-group preference atomically so a header selection cannot overwrite unrelated saved settings.
- Keep repositories and loaded scores in the shared ViewModel. Exclude group selection and membership from polling configuration; group switches must neither fetch data nor reset the slideshow deadline.
- Keep per-group scroll state through group changes and slideshow transitions.

## Tickers and refresh

- Weather slide: sports ticker follows the selected group by default. Optional all-five mode includes NFL, NCAAF, NHL, NBA and MLB, with live games first, then upcoming games and recent finals.
- Sports slide: retain the live weather ticker. Keep the 60 dp ticker container outside the slideshow animation.
- Stocks slide: show the independent HD-first stock ticker; keep its drawing offset across quote refreshes and slide visits. Feature HD beside eight rotating company cards, a range-cached Canvas chart and entitlement-aware index cards.
- Preserve the existing live-sports and weather refresh intervals, cancellation and failure handling. Keep all enabled feeds warm independently of the selected group. All-five ticker mode requests disabled dashboard feeds only while that ticker is enabled.

## Verification and delivery

- Add unit tests for default/custom groups, filtering, empty and overlapping memberships, recent-final ordering, and polling configuration independence.
- Write/reopen actual DataStore files to test memberships and active-group persistence, legacy defaults, unknown values and atomic group changes.
- Use virtual coroutine time to verify that switching groups mid-slide leaves weather and sports transitions on their original deadlines.
- Commit and push to the existing repository. The Android APK workflow must pass unit tests, lint, Kotlin/resource compilation and debug APK generation before publishing `OpenTicker-debug-apk`.
- Report the successful Actions run and downloadable APK. Meswao B3 installation and sustained device performance require physical tablet validation.

## Stock data boundary

- `StockRepository` / `StockDataSource` separate the provider from stock models, `StockMarketUiState`, `StockMarketViewModel` and Compose presentation. Each module caches independently; slide transitions never initiate requests.
- Keep private keys on an operator-managed HTTPS proxy. Ship a documented Massive reference adapter with a global request queue, coalescing, quota control and backoff. Leave prices unavailable until a licensed feed is configured.
- Label quote freshness and original timestamps, preserve valid values on failures, and expire cached provider market status. Use the provider's NYSE session status for holidays and early closes, with America/New_York dates for US history requests.
- Persist screen order, featured symbol, watchlist/categories, chart range, indices, rotation, ticker and refresh settings. Keep HD visible if the featured symbol changes. Describe Fortune 500 as a revenue ranking without asserting current membership or ranks.
- Test quotes, missing data, daily changes, chart mapping/caches, stale retention, backoff, session status, ticker formatting, preferences and slideshow timing using deterministic fixtures. Add offline server tests to the APK workflow and the stock screen to the emulator capture workflow. See [market data setup](STOCK_DATA.md).
