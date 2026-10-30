# Stock market data and deployment

The stock screen is enabled by default and rotates after weather and sports, ten seconds per screen. No quotes, credentials, or mock prices are bundled. Configure an HTTPS proxy in Settings to obtain market data. Weather and sports continue working without a stock service.

## Provider evaluation

Evaluated against the providers' published documentation on October 29, 2026. Plans and display rights must be verified for the intended installation before deployment.

| Provider | Free tier / limits | US quotes and history | Indices / freshness | Display and distribution |
| --- | --- | --- | --- | --- |
| [Alpaca](https://docs.alpaca.markets/us/docs/about-market-data-api) | Basic: free; 200 historical requests/minute | US stocks/ETFs; snapshots and bars since 2016. Explicit IEX feed on Basic. | Basic real-time covers IEX only; SIP requires appropriate subscription. Delayed SIP is explicit. This adapter has no cash-index feed. | Account and exchange agreements apply; API access does not by itself grant public redistribution rights. |
| [Massive](https://massive.com/stocks) | Stocks Basic: 5 requests/minute; two years of EOD aggregates | US stocks; daily/minute aggregate endpoints. Snapshot requires a paid plan. | EOD on Basic, delayed/real-time according to subscription. Indices require separate access. | [Individual vs business terms](https://massive.com/legal/terms) distinguish personal use from business use; a public or organizational display needs appropriate permission. |
| [Alpha Vantage](https://www.alphavantage.co/support/#api-key) | 25 requests/day on standard free service | Broad US coverage, daily history; intraday and quote recency require appropriate premium access | Quote endpoint defaults to end-of-day; real-time/delayed access is entitlement controlled | [Documentation](https://www.alphavantage.co/documentation/) directs commercial users to sales. 25 daily calls cannot sustain this watchlist at one-minute intervals. |
| [Twelve Data](https://twelvedata.com/pricing) | Basic: 8 credits/minute and 800/day; batch symbols consume credits separately | US equities and time-series endpoints; instrument access varies by plan | Quote and index coverage depend on subscription and exchange entitlements | Basic is described as internal non-display use; confirm display/redistribution rights. Do not assume a free API key grants dashboard rights. |

**Alpaca is the selected provider and the server's default.** The user-supplied `https://api.alpaca.markets` is Alpaca's upstream trading/clock host. Market-data requests use `https://data.alpaca.markets`. Neither URL is an OpenTicker proxy: the Android Settings URL must point to your own HTTPS proxy running the code below. Both Alpaca credentials remain on that server. No provider account, credentials or hosted proxy have been configured by this source change. The existing Massive adapter remains available as an alternative.

## Reference proxy

Requires Node.js 24; no npm packages. Run offline tests with:

```sh
node --test server/*.test.mjs
```

Configure only on the server, using your deployment secret manager or environment:

| Variable | Default / meaning |
| --- | --- |
| `MARKET_PROVIDER` | `alpaca` by default; alternatively `massive`. |
| `ALPACA_API_KEY_ID` / `ALPACA_API_SECRET_KEY` | Required Alpaca server secrets. Never Gradle properties, APK contents, URL parameters or Android preferences. |
| `ALPACA_FEED` | `iex` by default; `sip` only with real-time SIP access, or `delayed_sip` for explicit 15-minute delayed snapshots. The selected feed determines labels automatically. |
| `ALPACA_TRADING_BASE_URL` | `https://api.alpaca.markets` for live-account clock access; `https://paper-api.alpaca.markets` for paper credentials. Data requests always use `https://data.alpaca.markets`. |
| `MASSIVE_API_KEY` | Required only for the alternative Massive adapter. |
| `MARKET_DISPLAY_LICENSE_CONFIRMED` | Must be `true` after the operator verifies their account's display rights. |
| `MARKET_FRESHNESS` | Massive only: `PREVIOUS_CLOSE` by default, or entitled `DELAYED` / `REALTIME`. Alpaca labels come from `ALPACA_FEED` instead. |
| `MARKET_REQUESTS_PER_MINUTE` | Default `60` for Alpaca, `5` for Massive; set below the account quota, allowing for other clients sharing the key. Shared by every upstream endpoint and client. |
| `MARKET_INDICES_ENABLED` | Massive only; default `false`. Alpaca's stock adapter always returns unavailable cash-index values; it never substitutes ETFs. |
| `MARKET_ALLOWED_SYMBOLS` | Optional comma-separated server allowlist, e.g. `HD,AAPL,MSFT,BRK.B`. |
| `PORT` | `8787`; bound to loopback only. |

```sh
node server/start-market-proxy.mjs
```

Use `server/.env.example` as a configuration template. On your private server, set secrets through its environment/secret manager, or copy the template to an ignored `server/.env` and use `node --env-file=server/.env server/start-market-proxy.mjs`. Set the license flag only after confirming your account's permitted use. No keys are needed for offline tests.

Put the process behind a trusted HTTPS reverse proxy accessible to the tablet over a private network/VPN, or implement approved user authentication at the edge. The app supports a credential-free HTTPS URL on that private network; it does not implement login/OAuth. Do not expose the reference service as an unrestricted public redistribution API. Alpaca credentials are sent only in `APCA-API-KEY-ID` and `APCA-API-SECRET-KEY` request headers, with redirects disabled. The server uses GET snapshots, bars and clock only. It never requests account balances or sends orders. Credentials are never returned or logged. Android rejects HTTP, URL credentials/query strings/fragments, and known upstream provider hosts as proxy URLs.

The process uses a serialized upstream queue spaced slightly more than 60 seconds divided by the quota. Requests coalesce; failures retain valid values and back off up to one hour. HTTP responses return immediately from cache while work loads. Alpaca batches the watchlist in one snapshot request; each historical page gets its own queued request. Alpaca rate-reset/Retry-After headers can delay the whole queue. Multiple replicas require a shared limiter/cache; this reference is a single-process service. At Massive's five-request/minute default, a cold watchlist takes several minutes.

## Alpaca data mapping

[`/v2/stocks/snapshots`](https://docs.alpaca.markets/us/reference/stocksnapshots-1) supplies latest trade price and timestamp, previous daily close, daily high/low and volume. If the latest trade is missing, use a valid latest minute bar; never synthesize a quote or midpoint. All figures use the explicitly selected feed. IEX prices, high/low and volume cover a single exchange, and **IEX only** appears on quotes, charts and the ticker. SIP covers the consolidated US feed. A forbidden feed remains unavailable rather than silently switching sources or labels.

[`/v2/stocks/bars`](https://docs.alpaca.markets/us/reference/stockbars) supplies actual history: 1D uses five-minute bars for the current Eastern date; 1W/1M/3M/1Y use split-adjusted daily bars over 7/31/93/366 calendar days. Pagination completes before a new chart is published; refreshes retain the last complete chart. Delayed SIP history uses `feed=sip` with an explicit cutoff at least 15 minutes old. IEX history stays IEX. Closed/holiday sessions can return no current-day points and display an unavailable message; no prior-day series is relabeled 1D.

Alpaca's [`/v2/clock`](https://docs.alpaca.markets/us/reference/legacyclock) supplies `is_open` and its timestamp, handling regular sessions and calendar changes. Live and paper hosts are selectable independently of the data host. Missing/auth-failed clock data is unknown even when quotes work. The Android client expires old status rather than inferring availability from weekdays and local time. The included Alpaca stock adapter has no documented cash-index source, so S&P 500, Dow Jones and Nasdaq Composite cards stay neutral unavailable.

## Alternative Massive adapter

Previous-close mode requests the last 14 calendar days through the preceding Eastern calendar date, then uses the latest two actual trading bars returned. This conservative EOD mode updates today's completed session on the following Eastern date; it does not claim an intraday price. High, low and volume are labeled as session figures. Delayed/real-time stock modes use the actual most recent minute snapshot aggregate with its provider timestamp, never HTTP retrieval time. Index values use daily bars and remain labeled previous close even if the stock subscription is real-time. Unsupported instruments or entitlement failures remain unavailable. There are no ETF substitutes for missing indices.

The 1D chart requests actual five-minute bars for the current Eastern date, including any extended-session bars the provider returns. It may be unavailable on free plans, weekends, holidays, before trading begins, or without the appropriate entitlement. Other ranges use actual daily closing bars for 7/31/93/366 calendar-day lookbacks. The chart includes timestamps and freshness. It does not fabricate, interpolate, or substitute daily values for missing intraday points.

For Massive, market status comes from [`/v1/marketstatus/now`](https://massive.com/docs/rest/stocks/market-operations/market-status). For either provider, Android treats status older than 120 seconds, future-dated status, and missing status as unknown. Session status and quote freshness are independent: an open market can show delayed or previous-close data.

## Proxy contract

Append these paths to the Settings URL. All JSON timestamps are ISO-8601 UTC instants; monetary values are USD. Non-USD instruments are outside this service's supported universe.

`GET /v1/quotes?symbols=HD,AAPL,BRK.B&indices=I%3ASPX,I%3ADJI,I%3ACOMP`

```json
{
  "provider": "Provider attribution",
  "session": "UNKNOWN",
  "statusUpdated": null,
  "refreshAfterSeconds": 60,
  "pending": false,
  "staleSymbols": [],
  "warning": null,
  "quotes": [{"symbol":"HD","price":null,"previousClose":null,"high":null,"low":null,"volume":null,"updated":null,"freshness":"UNAVAILABLE","delayMinutes":null,"name":"The Home Depot","exchange":"NYSE"}]
}
```

`session`: `OPEN`, `CLOSED`, `UNKNOWN`. `freshness`: `REALTIME`, `DELAYED`, `PREVIOUS_CLOSE`, `UNAVAILABLE`. Set these from actual data and entitlements. An optional quote/history `feedLabel` carries coverage such as `IEX only` or `SIP`; clients show it beside freshness. Untimestamped prices stay unavailable. `staleSymbols` identifies retained quotes after failed refreshes; missing index entitlements do not mark valid stocks stale. `previousClose` is the comparison session's close: previous-close quotes compare to the preceding completed trading session. Missing fields are null, never zero-filled.

`GET /v1/history?symbol=HD&range=1D` (also `1W`, `1M`, `3M`, `1Y`):

```json
{"points":[],"freshness":"UNAVAILABLE","updated":null,"pending":false,"message":"Intraday data unavailable for this feed."}
```

Nonempty points have `time` and `close`, containing only provider-supplied values. `pending:true` requests another cache check after one minute; it does not grant permission to exceed upstream limits. The client sorts/deduplicates timestamps and rejects invalid or nonpositive values.

## Android behavior

Quotes and chart ranges cache independently in `CachedStockRepository`; weather and sports keep their existing caches. Transitions and card rotation never call a repository. Foreground/connectivity-aware polling runs independently of the slideshow. Stocks stop polling if that screen is disabled. During open sessions quotes refresh at the selected interval (minimum 60 seconds); closed/unknown sessions use at least 15 minutes. The provider's refresh floor and bounded failure backoff can lengthen this. Initial server loading is checked at the chosen open interval. Charts cache by symbol and range (1D: at least five minutes; others: six hours); pending charts are checked after a minute. Backgrounding cancels requests and removes animation producers.

Settings persist the ordered enabled-screen list, featured symbol, ordered watchlist (maximum 50), categories, range, ticker, rotation, interval, indices, and non-secret proxy URL in DataStore. Custom symbols use Other. Eight watchlist cards appear per visit; Home Depot stays pinned if another company is featured. The ticker always starts with HD. Fortune 500 is a company-revenue ranking, not a stock index; the configurable directory does not assert current membership or numerical ranks.

Quotes/history are in-memory only and are empty after a process restart. Errors preserve prior data and timestamps within the process. The reference server cache is also in-memory. A physical target-tablet installation and prolonged-operation check remain separate from JVM tests, lint, emulator preview, and APK compilation.
