# Deploy the OpenTicker market proxy on Cloudflare

This is a separate, lightweight serverless backend. The Android APK builds and runs without deploying it. An unset proxy URL shows **Stock data not configured**. This Worker supplies the existing Android HTTPS contract; no APK rebuild is needed after deployment.

Cloudflare [Workers Free supports SQLite Durable Objects](https://developers.cloudflare.com/durable-objects/platform/pricing/). The checked-in Wrangler configuration creates one SQLite object for persistent shared caches and provider request budgets. No database, VM, domain purchase or Node server is required. Cloudflare and provider free-tier limits still apply; exceeding Cloudflare limits returns unavailable data until reset. Provider subscriptions/display rights are separate from hosting.

## What you need

- A Cloudflare account, Node.js 22 or newer (24 recommended), and this repository.
- An Alpaca account with a market-data API key ID and secret. IEX is the default: **real-time, single-exchange coverage**, explicitly labeled `IEX only`. Consolidated SIP needs the corresponding provider entitlement. `delayed_sip` is explicitly labeled 15-minute delayed.
- Optional: a Massive API key with access to cash-index daily aggregates. Indices are labeled **Previous close**. Without it, index values are unavailable; the Worker never substitutes ETFs or invented values.
- Your intended company symbols. The defaults include HD and nineteen major companies. Android accepts up to 500 watchlist symbols and sends batches of at most 50. The server allowlist can contain the entire desired company universe. Fortune 500 is an annually changing revenue ranking; this project does not bundle or claim a current licensed membership list. Supply the exchange tickers for the list you want, including share-class notation such as `BRK.B`.

## Deploy, step by step

Run these commands from the repository root in PowerShell or a terminal. No provider credential belongs in a command argument, source file, Android setting, Gradle property or GitHub secret for the APK build.

```sh
cd server/cloudflare
npm ci
npm test
npm run check
npx wrangler login
```

`npm run check` builds the bundle without publishing or needing any account secrets. `wrangler login` opens Cloudflare's browser sign-in.

Edit **non-secret** settings in `wrangler.jsonc`:

1. Change `name` if `openticker-market` is unsuitable for your account.
2. Keep `ALPACA_FEED` as `iex` for explicit IEX coverage, or select your entitled `sip` / `delayed_sip` feed. No automatic feed switching occurs after a provider error.
3. Keep `ALPACA_TRADING_BASE_URL` as `https://api.alpaca.markets` for live keys, or use `https://paper-api.alpaca.markets` for paper keys. This only changes the market-clock host; price/history data always uses `data.alpaca.markets`.
4. Set `MARKET_ALLOWED_SYMBOLS` to a comma-separated allowlist matching your Android watchlist. Include `HD` and any alternative featured symbol. Large company universes are supported; each request is bounded to 52 symbols.
5. Keep request budgets below your provider allowances, accounting for other apps sharing the account. Defaults: Alpaca 60/minute, Massive 5/minute.
6. After checking the provider's terms for your intended display, change `MARKET_DISPLAY_LICENSE_CONFIRMED` to `true`. A publicly reachable URL does not grant redistribution rights; use appropriate network restrictions for a private display if your agreements require them.

Create the Worker and its Durable Object:

```sh
npm run deploy
```

Wrangler prints a URL such as `https://openticker-market.YOUR-SUBDOMAIN.workers.dev`. Until you add secrets, quote/history requests return HTTP 503 with `Stock data not configured`; the service does not crash.

Add provider credentials using Cloudflare's interactive secret prompts:

```sh
npx wrangler secret put ALPACA_API_KEY_ID
npx wrangler secret put ALPACA_API_SECRET_KEY
```

Paste each value only when Wrangler prompts. These [platform secrets](https://developers.cloudflare.com/workers/configuration/secrets/) are available only to the Worker. The app never receives them. You may also add them in Cloudflare Dashboard → Workers & Pages → this Worker → Settings → Variables and Secrets → Add → Secret, then deploy the change.

For optional real cash-index values:

```sh
npx wrangler secret put MASSIVE_API_KEY
```

The price/history provider remains Alpaca. The Massive key is used solely for the documented index aggregates endpoint and is sent in an Authorization header.

## Verify and connect Android

Replace the hostname below with the URL Wrangler printed:

```sh
curl "https://openticker-market.YOUR-SUBDOMAIN.workers.dev/health"
curl "https://openticker-market.YOUR-SUBDOMAIN.workers.dev/v1/quotes?symbols=HD,AAPL,MSFT&indices=I:SPX,I:DJI,I:COMP"
curl "https://openticker-market.YOUR-SUBDOMAIN.workers.dev/v1/history?symbol=HD&range=1D"
curl "https://openticker-market.YOUR-SUBDOMAIN.workers.dev/v1/indices"
```

In Windows PowerShell use `curl.exe` if `curl` is an alias. `/health` reports configuration booleans only, never secrets. Empty current-day history is normal before trading, on a holiday, or without that feed's entitlement. A market-clock error can coexist with valid quotes and shows unknown market status.

On the tablet, open OpenTicker → Settings → **Market-data proxy HTTPS URL**, enter only the base URL (`https://openticker-market.YOUR-SUBDOMAIN.workers.dev`), and save. Do not enter an Alpaca/Massive URL, endpoint path, query string or API key. Weather and sports continue functioning during setup and proxy outages.

## Endpoints and behavior

| Endpoint | Input | Result |
| --- | --- | --- |
| `/health` | None | Configuration state; no upstream requests. |
| `/v1/quotes` | `symbols=HD,AAPL` and optional `indices=I:SPX,I:DJI,I:COMP` | Batched stock quotes, original timestamps, freshness, provider session, stale symbols and warnings. |
| `/v1/history` | `symbol=HD&range=1D` (`1W`, `1M`, `3M`, `1Y` also supported) | Actual five-minute intraday or split-adjusted daily bars, chronological and deduplicated. |
| `/v1/indices` | Optional `symbols=I:SPX,I:DJI,I:COMP` (default all three) | Actual cash-index EOD values or explicit unavailable values. Also included through `/v1/quotes`. |

Only GET is supported. Unknown routes return 404, unapproved symbols/invalid ranges 400, and missing configuration/storage failures 503. Provider outages normally return HTTP 200 with stale original values or neutral unavailable data plus a warning, allowing the Android cache to retain valid data. Proxy responses themselves are `Cache-Control: no-store`; persistence happens inside the Durable Object, never in a public shared response cache.

Quotes and clock cache for 60 seconds, indices for 15 minutes, 1D history for five minutes, and longer history for six hours. A single named object serializes refreshes and coalesces cached work across tablets and Worker isolates. Rolling one-minute provider budgets persist across restarts. 429 `Retry-After`/rate-reset headers block all further calls to that provider until the deadline. Failed cache keys back off from one minute to one hour while retaining original timestamps. Quote and index clocks remain independent, so unavailable indices do not slow stocks.

History publishes only complete pagination (up to ten pages per refresh). Failures retain the previous complete chart. A new Eastern date uses a separate intraday cache, so yesterday's chart is never labeled today. Delayed SIP bars use the documented SIP history feed with a cutoff at least 15 minutes old. Nothing runs in the background when no clients request data.

Cache scope is one Worker deployment. Use one deployment per provider account/budget, or reduce budgets when other applications share the keys. Changing the feed starts a distinct stock/history cache. The allowlist bounds public requests but is not authentication; add network access controls appropriate to your intended private installation. Do not log secrets or paste credential-bearing request/response dumps into issues.

## Test and update

```sh
npm test
npm run check
npm run deploy
```

CI runs offline provider/Worker tests, validates the Wrangler bundle, and builds Android without deploying anything. To rotate credentials, repeat the appropriate `wrangler secret put` command. Do not delete the Durable Object migration or change its class name when deploying ordinary updates.

For local development, use `npm run dev`. Local credentials may be supplied in a **gitignored** `.dev.vars` file or local environment; never commit it. `.dev.vars`, `.wrangler`, `node_modules`, and private `.env` files are excluded from Git.

Provider references: [Alpaca market data coverage](https://docs.alpaca.markets/us/docs/about-market-data-api), [stock snapshots](https://docs.alpaca.markets/us/reference/stocksnapshots-1), [stock bars](https://docs.alpaca.markets/us/reference/stockbars), [provider clock](https://docs.alpaca.markets/us/reference/legacyclock), [Massive index daily aggregates](https://massive.com/docs/rest/indices/aggregates/custom-bars).
