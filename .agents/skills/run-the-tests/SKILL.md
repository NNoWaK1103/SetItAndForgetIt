---
name: run-the-tests
description: Use after changes to OpenTicker Kotlin/Compose, Node market server, Cloudflare Worker, or CI/configuration, and whenever test status or release readiness needs proof.
---

# Run the tests: commands and evidence

Run focused tests first, then the relevant comprehensive gate. From the repository root on a machine with the required SDK/toolchain:

## Node market server and Worker fixtures
```sh
node --test server/*.test.mjs server/cloudflare/*.test.mjs
```

## Cloudflare Worker bundle, offline unit tests and runtime checks
```sh
cd server/cloudflare
npm ci
npm test
npm run check
```
`npm run check` is a *dry-run build*, not a deployment. Do not run `npm run deploy` or `npm run verify` against production without explicit authorization.

## Android Kotlin/Compose tests, lint and both build variants
```sh
./gradlew --no-daemon testDebugUnitTest testDashboardPreviewUnitTest lintDebug lintDashboardPreview assembleDebug assembleDashboardPreview
```
Windows developers may use `gradlew.bat`. Android SDK 36, Java 17 and the Android toolchain are required.

## Agent configuration only
```sh
python3 scripts/verify-agent-config.py
```

## Emulator and device checks
Inspect `.github/workflows/android-preview.yml` for actual Pixel C emulator capture and `scripts/verify-playback.mjs`. Use GitHub Actions output for emulator evidence. Real-tablet compatibility and real Alpaca data require separate authorized tests; fixture and preview successes cannot prove either.

## Reporting standard
For each test category report: command, environment, PASS/FAIL/BLOCKED/NOT RUN, output evidence (exit code, test count or CI URL), and limitations. If a test fails, invoke `systematic-debugging`. Never fabricate executed tests or silently downgrade failures.
