# Private Free Worker validation, November 7, 2026

Prepared on branch `feature/free-worker-auth` from local OpenTicker 1.4.1 (`4bb3328`). Remote `main` was `0008c2d` when fetched. No merge, push, Cloudflare sign-in/deployment, provider login, or credential provisioning was performed.

## Passed

- 41 Node provider/Worker fixture tests: preserved quote/history/feed behavior, explicit IEX/SIP labels, shared provider budgets and cache reuse, missing/wrong-token rejection before storage/provider calls, direct Durable Object authentication, token rotation, scope/input restrictions, no credential forwarding, and persistent private request limits.
- One additional local workerd + SQLite Durable Object integration test: unauthenticated requests rejected, authenticated health and fixture quote succeeds, cache reused, and a provider redirect never reaches its target.
- Wrangler dry-run bundle validation. No publishing or account secrets required.
- 146 Android JVM unit tests, including encrypted persistence contract, atomic save failure, URL-change clearing, token redaction, missing/malformed token failures, stock-only request headers, and disabled redirects.
- Android `lintDebug` passed with no errors and one existing `DiscouragedApi` warning.
- `assembleDashboardPreview` passed for version 1.4.2, versionCode 9. Preview signing certificate matches the prior 1.4.1 preview.
- Focused security review; the verifier now prints only locally authored verification messages and masks other errors, preventing arbitrary response data from appearing in exception output.
- `git diff --check` and verification-script syntax check passed.

The local Cloudflare runtime test caught two pre-existing runtime incompatibilities that mocked Node tests missed. The global fetch function is now called without an invalid object receiver. Cloudflare supports manual redirects rather than `redirect: error`, so upstream requests use manual mode and reject every non-2xx response, including all redirects.

## Not yet verified

- The user's Cloudflare account access, actual plan, deployed URL, production limits/CPU usage, or live deployment. Existing account access was reported blocked; no alternate access attempt was made here.
- Real Alpaca credentials, feed entitlements, display rights or production quote/history responses. All exercised credentials and market responses were deliberately fake fixtures.
- Hardware Android Keystore behavior and physical-tablet Settings/keyboard/rendering, restart and prolonged operation. Host persistence tests use an injected test cipher; hardware encryption is not claimed as tested.

## Remaining setup

Follow `server/cloudflare/README.md` on your own computer. Confirm Workers Free, deploy fail-closed without secrets, privately create the tablet token, and run the access-control verifier before adding Alpaca credentials. The verifier's live result is required; local tests are not a substitute. Keep existing security rules enabled and stop rather than accepting a paid plan or bypassing denied access.
