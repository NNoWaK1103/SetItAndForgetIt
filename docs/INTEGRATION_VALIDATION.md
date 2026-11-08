# OpenTicker 1.5.1 integration validation

Prepared November 8, 2026 against remote `main` at `0008c2d664abfd5c7a2e93130d7f3bb1e56f4eca`.

## Included work

- Last-good feed persistence, atomic settings saves, and separate service/network status.
- Hands-free sports, weather and stock browsing, richer provider context, and persistent information wires.
- Mandatory private Worker access token, encrypted URL-bound Android storage, restricted redirects, persistent request/provider budgets, and local SQLite runtime checks. Includes the Windows `fileURLToPath` runtime-test correction.
- Philadelphia / Birds Eye GUI, bundled licensed typography, Eastern clock, Philly-first real games, and current-day confirmed-empty sports hiding. Unknown or failed feeds remain visible and keep polling.
- Previous completed-day stock-close fallback with original provider timestamps and honest `PREVIOUS_CLOSE` labels. No fabricated prices or intraday points.
- Updated tablet automation for current navigation and Save/Cancel behavior. CI now tests and builds the standard and isolated GUI-preview variants.

The Philly 1.5.1 source package was applied over the preserved reliability work, then the last-price source/test/documentation patch applied without conflicts. Existing bottom-wire source was preserved. No credentials, private signing material, local SDK paths, build outputs or dependencies are committed.

## Passed locally

- Android aggregate: `testDebugUnitTest testDashboardPreviewUnitTest lintDebug lintDashboardPreview assembleDebug assembleDashboardPreview`.
- 171 JVM tests for each variant; zero failures, errors or skipped tests.
- Both lint checks: zero errors and one existing `DiscouragedApi` warning each.
- Both APK variants assembled.
- 51 Node provider/Worker fixture tests.
- Worker command: 32 passing tests, consisting of 30 overlapping Worker fixtures and two additional workerd + SQLite integration tests. All provider responses and credentials were local fixtures.
- Wrangler bundle dry run, with no deployment.
- Node syntax checks for playback and deployment verification; Python syntax check for screenshot capture; `git diff --check`.
- Independent review corrected stale tablet-script navigation and interval-cancellation assumptions; no additional blocking finding was established in the reviewed changes.

## Remaining limits

The updated emulator scripts were syntax-checked, not executed against an Android UI. The available emulator setup was not usable during this integration. Physical-tablet rendering, accessibility, interruption recovery and prolonged operation remain unverified. Local tests do not validate actual stock-account credentials, entitlements, live provider responses or the user's deployed Worker.

Repository publication does not deploy the Worker or change account secrets. Merging updates the standard app source as well as the separate preview target, and triggers the repository's APK and tablet-preview workflows. Installation still requires matching package and signing identity; do not uninstall an existing app to bypass a signature mismatch. CI results are tracked on the pull request and may supersede this local record.
