# Stock market data and deployment

The stock screen is enabled by default and rotates after weather and sports, ten seconds per screen. No quotes, credentials, or mock prices are bundled. Configure an HTTPS proxy in Settings to obtain market data. Weather and sports continue working without a stock service.

## Provider evaluation

Evaluated against the providers' published documentation on October 29, 2026. Plans and display rights must be verified for the intended installation before deployment.

| Provider | Free tier / limits | US quotes and history | Indices / freshness | Display and distribution |
| --- | --- | --- | --- | --- |
| [Massive](https://massive.com/stocks) | Stocks Basic: 5 requests/minute; two years of EOD aggregates | US stocks; daily/minute aggregate endpoints. Snapshot requires a paid plan. | EOD on Basic, delayed/real-time according to subscription. Indices require separate access. | [Individual vs business terms](https://massive.com/legal/terms) distinguish personal use from business use; a public or organizational display needs appropriate permission. |
| [Alpha Vantage](https://www.alphavantage.co/support/#api-key) | 25 requests/day on standard free service | Broad US coverage, daily history; intraday and quote recency require appropriate premium access | Quote endpoint defaults to end-of-day; real-time/delayed access is entitlement controlled | [Documentation](https://www.alphavantage.co/documentation/) directs commercial users to sales. 25 daily calls cannot sustain this watchlist at one-minute intervals. |
| [Twelve Data](https://twelvedata.com/pricing) | Basic: 8 credits/minute and 800/day; batch symbols consume credits separately | US equities and time-series endpoints; instrument access varies by plan | Quote and index coverage depend on subscription and exchange entitlements | Basic is described as internal non-display use; confirm display/redistribution rights. Do not assume a free API key grants dashboard rights. |

The included reference server uses Massive because it has documented aggregate, snapshot, and market-status endpoints. **This is an implementation choice, not a bundled data license.** The operator supplies an appropriately licensed account and confirms display rights. A different provider can implement the same proxy contract without changing the Android data/UI layers. No provider has been configured or deployed by this source change.

## Reference proxy

Requires Node.js 24; no npm packages. Run offline tests with:

```sh
node --test server/market-proxy.test.mjs
```

Configure only on the server, using your deployment secret manager or environment:

| Variable | Default / meaning |
| --- | --- |
| `MASSIVE_API_KEY` | Required private server secret. Never a Gradle property, build secret copied to the APK, URL query, or Android preference. |
| `MARKET_DISPLAY_LICENSE_CONFIRMED` | Must be `true` after the operator verifies their account's display rights. |
| `MARKET_FRESHNESS` | `PREVIOUS_CLOSE` by default. `DELAYED` (15 minutes) or `REALTIME` only with the matching licensed snapshot entitlement. |
| `MARKET_REQUESTS_PER_MINUTE` | `5`; set at or below the purchased quota. Shared by every upstream endpoint and every connected client. |
| `MARKET_INDICES_ENABLED` | `false`. Enable only with licensed index aggregate access. Otherwise indices stay unavailable. |
| `MARKET_ALLOWED_SYMBOLS` | Optional comma-separated server allowlist, e.g. `HD,AAPL,MSFT,BRK.B`. |
| `PORT` | `8787`; bound to loopback only. |

```sh
node server/market-proxy.mjs
```

Put the process behind a trusted HTTPS reverse proxy, accessible to the tablet over a private network/VPN. Keep unauthorized users off that network, or implement provider-approved user authentication at the edge. The app currently supports a credential-free HTTPS service URL on such a private network; it does not implement login/OAuth. Do not expose this reference service as an unrestricted public redistribution API. API keys are sent upstream in the Authorization header and are never returned or logged by the server. Do not include credentials in the configured proxy URL. Android rejects HTTP, URL userinfo, query strings and fragments in the service URL.

The reference process uses a serialized upstream queue with spacing slightly greater than 60 seconds divided by the configured quota. Requests for a cache key coalesce, failures retain previous valid values and retry with exponential backoff (up to one hour). HTTP responses return immediately from cache while work loads in the background. At 5 requests/minute, the default watchlist takes several minutes to populate on a cold server. Multiple server replicas would require a shared limiter/cache; this reference server is a single-process service. Keep unrelated consumers of the same provider key within the total quota.

Previous-close mode requests the last 14 calendar days through the preceding Eastern calendar date, then uses the latest two actual trading bars returned. This conservative EOD mode updates today's completed session on the following Eastern date; it does not claim an intraday price. High, low and volume are labeled as session figures. Delayed/real-time stock modes use the actual most recent minute snapshot aggregate with its provider timestamp, never HTTP retrieval time. Index values use daily bars and remain labeled previous close even if the stock subscription is real-time. Unsupported instruments or entitlement failures remain unavailable. There are no ETF substitutes for missing indices.

The 1D chart requests actual five-minute bars for the current Eastern date, including any extended-session bars the provider returns. It may be unavailable on free plans, weekends, holidays, before trading begins, or without the appropriate entitlement. Other ranges use actual daily closing bars for 7/31/93/366 calendar-day lookbacks. The chart includes timestamps and freshness. It does not fabricate, interpolate, or substitute daily values for missing intraday points.

Market status comes from [`/v1/marketstatus/now`](https://massive.com/docs/rest/stocks/market-operations/market-status), specifically NYSE regular-session status. That provider handles holidays, early closures and DST. Android treats status older than 120 seconds, future-dated status, and missing status as unknown. This avoids displaying OPEN overnight from a retained response. Session status and quote freshness are independent: an open market can still show delayed or previous-close data.

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

`session`: `OPEN`, `CLOSED`, `UNKNOWN`. `freshness`: `REALTIME`, `DELAYED`, `PREVIOUS_CLOSE`, `UNAVAILABLE`. The proxy must assign these from the purchased entitlement and actual returned data. Unknown or untimestamped prices are unavailable in Android. `staleSymbols` identifies retained quotes whose upstream refresh failed; unavailable index entitlements do not mark valid stock quotes stale. `previousClose` means the comparison session's close: for a previous-close quote, use the preceding completed trading session, so daily changes compare like periods. Missing fields are null, never zero-filled.

`GET /v1/history?symbol=HD&range=1D` (also `1W`, `1M`, `3M`, `1Y`):

```json
{"points":[],"freshness":"UNAVAILABLE","updated":null,"pending":false,"message":"Intraday data unavailable for this feed."}
```

Nonempty points have `time` and `close`, containing only provider-supplied values. `pending:true` requests another cache check after one minute; it does not grant permission to exceed upstream limits. The client sorts/deduplicates timestamps and rejects invalid or nonpositive values.

## Android behavior

Quotes and chart ranges cache independently in `CachedStockRepository`; weather and sports keep their existing caches. Transitions and card rotation never call a repository. Foreground/connectivity-aware polling runs independently of the slideshow. Stocks stop polling if that screen is disabled. During open sessions quotes refresh at the selected interval (minimum 60 seconds); closed/unknown sessions use at least 15 minutes. The provider's refresh floor and bounded failure backoff can lengthen this. Initial server loading is checked at the chosen open interval. Charts cache by symbol and range (1D: at least five minutes; others: six hours); pending charts are checked after a minute. Backgrounding cancels requests and removes animation producers.

Settings persist the ordered enabled-screen list, featured symbol, ordered watchlist (maximum 50), categories, range, ticker, rotation, interval, indices, and non-secret proxy URL in DataStore. Custom symbols use Other. Eight watchlist cards appear per visit; Home Depot stays pinned if another company is featured. The ticker always starts with HD. Fortune 500 is a company-revenue ranking, not a stock index; the configurable directory does not assert current membership or numerical ranks.

Quotes/history are in-memory only and are empty after a process restart. Errors preserve prior data and timestamps within the process. The reference server cache is also in-memory. A physical target-tablet installation and prolonged-operation check remain separate from JVM tests, lint, emulator preview, and APK compilation.
