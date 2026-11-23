---
name: market-data-verification
description: Verify OpenTicker quote and chart changes across the Kotlin app, Node adapter and Cloudflare Worker with dated and unavailable data.
---

# Verify OpenTicker market data

1. Identify the changed layer and the symbol, chart range and market session that reproduce the issue. Inspect `docs/STOCK_DATA.md`, `server/cloudflare/worker.test.mjs` and the applicable stock tests under `app/src/test/java/com/openticker/app/`.
2. Run `node --test server/*.test.mjs server/cloudflare/*.test.mjs`. For Worker changes also run `npm ci`, `npm test` and `npm run check` in `server/cloudflare`. The last command bundles the Worker in dry-run mode. For Android changes run the relevant Kotlin tests for debug and dashboardPreview using `run-the-tests`.
3. Use controlled responses to check quotes and history separately. Include a completed trading day, stale cache, unavailable data and provider throttling where relevant. Check that symbol, timeframe, provider coverage and market timestamp survive into the displayed state. Compare the market timestamp with the requested session rather than the refresh time.
4. For an unpaired preview, run `.maestro/stock-dashboard.yaml` on a disposable tablet to capture the setup state. For an explicitly authorized live check use the existing interactive verifier described in `server/cloudflare/README.md`. Keep private tokens out of logs, commands and screenshots. Report fixture, emulator and live results separately, including the ranges actually checked.
