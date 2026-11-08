# Private OpenTicker Worker on Cloudflare Free

This is a prepared, tested source package, not a deployed service. It uses Workers Free and one SQLite Durable Object. No paid Cloudflare Access subscription, domain purchase, VM, or external database is needed. **Keep the Cloudflare account on Workers Free.** If setup asks for a card or an upgrade, stop rather than accepting it. The source cannot choose or guarantee your account's billing plan.

The Worker requires a private access token on **every** endpoint, including health. Without a correctly configured token it returns 503. Missing/wrong client tokens return 401 **before** touching storage or Alpaca. Provider credentials remain Cloudflare secrets. This replaces the old unauthenticated reference Worker; it does not bypass or disable existing security rules.

Use OpenTicker **1.4.2 or newer** for this connection. Previous APKs cannot send the token. Weather and sports still work without stocks. A preview APK installs separately as OpenTicker Preview, with its own settings; it does not replace the original app.

## What is free, and what is not promised

Cloudflare documentation checked November 7, 2026:

- [Workers Free](https://developers.cloudflare.com/workers/platform/pricing/): 100,000 requests/day, 10 ms CPU/request. [Limits](https://developers.cloudflare.com/workers/platform/limits/): 50 subrequests/request. The small front Worker authenticates and forwards to the Durable Object.
- [SQLite Durable Objects on Free](https://developers.cloudflare.com/durable-objects/platform/pricing/): 100,000 requests/day, 13,000 GB-seconds/day, 5 million rows read/day, 100,000 rows written/day, and 5 GB total storage. Exceeding a Free limit causes errors; daily allowances reset at 00:00 UTC. Account-wide usage by other Workers also matters.
- This application adds an authenticated request ceiling of 60/minute and 10,000/day across tablets, plus provider request budgets and cache reuse. Large watchlists may need a slower refresh. These controls reduce usage but are not a guarantee against quota exhaustion or denial of service. Unauthenticated requests still count toward the Cloudflare Worker request limit.
- [Alpaca Basic](https://docs.alpaca.markets/us/docs/about-market-data-api) is free, with real-time **IEX only**, not every US exchange, and 200 historical requests/minute. The default upstream budget here is 60/minute. Plans, history restrictions and exchange agreements still apply. **Verify your own private display rights before enabling the license flag.** API availability is not permission to redistribute data publicly or commercially.
- Cash indices have no substitute values. They remain unavailable unless you separately configure an entitled Massive account; that optional provider is not part of the free guarantee. Leave it unset for this no-paid-service setup.

## One-time setup on your computer

You need your Cloudflare account, your own permitted Alpaca API key ID/secret, Node.js 22+ (24 recommended), and this repository/source ZIP. Do not paste any credential into a chat, issue, URL, screenshot, APK build setting, or command argument.

### 1. Test the source, then sign in yourself

From the repository root:

```sh
cd server/cloudflare
npm ci
npm test
npm run check
npx wrangler login
```

Tests use deliberately fake fixture credentials and never contact Alpaca. The runtime test exercises real local workerd and SQLite Durable Objects with mocked upstream responses. `check` bundles without publishing. `login` is a user-operated Cloudflare sign-in, not a request to share the login with anyone.

In Cloudflare, verify the account's Workers plan says **Free**. This project does not need a Zero Trust organization or a paid Access plan. Do not remove any existing rule or work around a 403; resolve account access separately if Cloudflare will not let you proceed.

### 2. Deploy the fail-closed Worker with no secrets

Review `wrangler.jsonc`:

- Choose a unique `name` if needed. Keep the SQLite migration and class name unchanged.
- Keep `ALPACA_FEED` set to `iex`. Only choose SIP/delayed SIP if your entitlement permits it; no feed switching happens automatically.
- `ALPACA_TRADING_BASE_URL` is `https://api.alpaca.markets` for live keys, or `https://paper-api.alpaca.markets` for paper keys. This affects only the GET market clock. Quotes/history always go to `https://data.alpaca.markets`.
- Set `MARKET_ALLOWED_SYMBOLS` to your watchlist, including HD. Defaults cover twenty companies. The app supports up to 500 symbols in batches of fifty, but all requested symbols must be allowed here.
- Leave `MARKET_DISPLAY_LICENSE_CONFIRMED` as `false` for now. Do not place tokens or API keys in this JSON.

```sh
npm run deploy
```

Wrangler prints your actual `https://NAME.SUBDOMAIN.workers.dev` URL. This first deployment exposes no data: missing authentication configuration fails closed. Preview URLs and application observability are disabled. No provider secrets have been added yet.

### 3. Create and add your private tablet token yourself

On **your computer**, generate a fresh random token:

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
```

Keep this 64-character lowercase hexadecimal token privately. It grants read access to your stock proxy. It is **not** an Alpaca key. Do not reuse a test fixture or a human-made password. Add it at the interactive prompt:

```sh
npx wrangler secret put PROXY_ACCESS_TOKEN
```

Do not put the value after the command. You can instead use Cloudflare Dashboard → Workers & Pages → your Worker → Settings → Variables and Secrets → Add → **Secret**. Save/deploy that change. Avoid screen sharing while entering secrets.

Verify the live endpoint **before adding provider credentials**:

```sh
npm run verify
```

The verifier asks for the Worker URL and the token with input hidden. It rejects redirects, never prints the token, and tests that missing and wrong tokens cannot read any endpoint. Expect: access-control checks PASS, provider not configured. If any access-control check fails, stop. Never turn off a protection to make a test pass.

### 4. Add Alpaca secrets, then enable your permitted display

Only after verification passes:

```sh
npx wrangler secret put ALPACA_API_KEY_ID
npx wrangler secret put ALPACA_API_SECRET_KEY
```

Paste values only at each prompt, or add them as Dashboard Secrets. This code sends them only to the fixed Alpaca market-data/clock hosts using `APCA-API-KEY-ID` and `APCA-API-SECRET-KEY`. Redirects are rejected. It uses GET snapshots, bars and clock, never balances, account data, trades or orders. Alpaca credentials may have broader powers, so protect them even though this code is read-only.

After you confirm your account permits this private display, set `MARKET_DISPLAY_LICENSE_CONFIRMED` to `true` in `wrangler.jsonc`, then:

```sh
npm run deploy
npm run verify
```

Expect access-control PASS. A valid quote is a separate provider check. Empty 1D history can be normal before trading, on holidays/weekends, or without the feed entitlement. A 403 requires correcting credentials/entitlements or Cloudflare account access, not bypassing rules. No live provider success is implied by local tests.

### 5. Connect the tablet

Install the supplied 1.4.2 preview APK, or build `assembleDashboardPreview`. In OpenTicker Preview → Settings, enter the actual Worker base HTTPS URL and its **Worker access token (not Alpaca keys)**, then Save. Never enter Alpaca keys in Android. Use the exact generated token from step 3.

The app encrypts the token with an Android Keystore AES-GCM key, binds it to the saved proxy address, hides its contents in Settings, disables stock-request redirects, and keeps it out of backups, logs and APK source. Changing the proxy address requires its own token. Cancel/Back discards an unsaved token. A protected-device compromise can still expose credentials at runtime; this is not device attestation or multi-user login.

Keep the original app installed. The preview uses separate settings. For future updates, use the same package/signing certificate. **Never uninstall the existing app to get past a signature mismatch**, because that removes its settings and saved data.

## Maintenance and behavior

- To revoke access: remove/replace `PROXY_ACCESS_TOKEN` in Cloudflare. Requests with the old token immediately fail against the new deployment. Enter the replacement locally on each authorized tablet. There is one deployment-wide token, not individual device revocation.
- To stop serving provider data: set the license flag false and deploy, or remove the provider secrets. All endpoints remain protected. Do not roll back to the old unauthenticated Worker while provider credentials exist.
- Tokens never appear in cache keys or provider requests. JSON responses use `Cache-Control: no-store`, no permissive CORS headers, and generic errors. The authenticated `/health` reports only configuration booleans.
- Exact GET routes: `/health`, `/v1/quotes?symbols=HD,AAPL`, `/v1/history?symbol=HD&range=1D`, `/v1/indices`. Unexpected/duplicate query parameters, unsupported methods, unknown paths and overly long URLs are rejected. Authentication precedes routing.
- Quotes/clock cache for 60 seconds, history for five minutes (1D) or six hours (other ranges), indices for fifteen minutes. Original timestamps and IEX/SIP/delay labels stay intact. Failures retain valid previous values, with stale/unavailable notices; there are no fabricated values or ETF substitutes for cash indices.
- When a successful Alpaca snapshot has no valid latest trade or minute bar, stocks can use its newest valid daily/previous-daily close from a prior Eastern calendar date. The app labels this **Previous close · IEX only** (or the configured feed), retains the provider's original timestamp, and never labels this fallback real-time or delayed. Current-day, future, untimestamped and invalid bars are ignored. A newer cached quote is retained instead of replacing it with an older bar. If only the previous-daily bar is available, the comparison close/change stays unavailable rather than being invented. This adds no upstream requests and does not fill missing intraday charts.
- A last-price fallback cannot fix failed provider authentication, missing entitlements or a network failure. With no previously cached value and no valid provider bar, the price remains unavailable. Failed-cache backoff survives deployments and can last up to an hour; do not clear durable storage or repeatedly rotate credentials to bypass it.
- One named SQLite Durable Object serializes provider traffic, shares cache/budgets across tablets, and persists quota state across restarts. 429 provider responses cause shared backoff. No scheduled jobs run when no clients request data. Real Cloudflare quotas and your provider limits still apply.
- The old Node proxy is unchanged and unauthenticated; do not expose it publicly. This Android protected connection is intended for the secured Worker, not that older setup.

Useful references: [Cloudflare secrets](https://developers.cloudflare.com/workers/configuration/secrets/), [Alpaca authentication FAQ](https://docs.alpaca.markets/us/docs/market-data-faq), [snapshots](https://docs.alpaca.markets/us/reference/stocksnapshots-1), [bars](https://docs.alpaca.markets/us/reference/stockbars).
