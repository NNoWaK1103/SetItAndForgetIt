# Philadelphia / Birds Eye Preview 1.5.1

Updated November 8, 2026 in response to the user's request to match the published Figma GUI and include its Philadelphia identity, while keeping the existing bottom wires and hiding sports with no games today.

Reference: https://preset-elude-78075939.figma.site

## Philadelphia design

- Reproduces the reference's supplied bird vector, PHILADELPHIA / BIRDS EYE branding and actual Barlow Condensed typeface. Barlow is bundled locally with its SIL Open Font License.
- Header provides NFL / College, NHL, MLB, NBA, Weather and Stocks tabs, a correctly labeled Philadelphia/Eastern clock, native refresh/settings and retained playback pause control.
- Football uses paired NFL/college panels; single-sport screens use the reference's two-column game-card grid on wide tablets. Team abbreviation badges, scores, records and card details come from actual providers. Eagles, Flyers, Phillies and 76ers games are prioritized without removing other games.
- Weather and Market layouts now follow the reference's normal-tablet arrangement rather than prematurely falling back to compact pages. Weather retains all 12 hourly forecasts and seven daily entries; market retains genuine prices/history and explicit unavailable/setup states.
- Both bottom wire source files are byte-identical to the first GUI preview. Their visuals, scrolling, labels and persistent market-wire behavior were not changed.
- Fresh settings default to Philadelphia and sports-first playback. Explicit saved city, startup screen, stock connection and other settings remain respected. Saved custom sports groups retain group mode; standard sport tabs automatically cycle through available sports after their last game page.
- No prototype scores, weather readings, portfolio percentages, news or market values were copied into runtime data. There is no demo feed pretending to be live.

## Hide sports with no games

Only a successful empty response for the explicitly requested current Philadelphia/Eastern date can hide a league. It must be online, not loading/restored/errored, and no older than five minutes. Missing, malformed, offline, failed, stale, future-dated or yesterday's data remains visible as unknown. Any actual game, including final/postponed/canceled entries, keeps its sport visible.

Hidden leagues stay on the normal polling schedule and reappear when games become available or their status becomes unknown. Midnight forces a new date-scoped refresh. Empty leagues disappear from both top tabs and panels; NFL/College becomes NFL or College when only one remains. All-empty days skip the automatic Sports slide without changing saved preferences. The manually selected, Sports-only edge uses a quiet no-games fallback.

## Verification

- Final aggregate passed after all review fixes: debug and preview unit tests, lint and APK assembly.
- 170 JVM tests passed per variant, zero failures/errors/skips. New coverage includes Philadelphia navigation, saved custom groups, same-day empty schedules, loading/error/offline/restored/stale states, midnight, malformed responses, schedule changes and the manual-Sports all-empty edge.
- Both lint tasks passed with zero errors and six warnings: five dependency-update advisories and the existing DiscouragedApi warning. Pinned dependencies were not upgraded as part of this UI work.
- 41 Node/provider/Worker fixture tests passed again. The additional local Worker/SQLite integration test also passed (21 checks in that run, including 20 overlapping Worker fixture tests).
- Independent review findings were fixed: saved-group startup, cross-tab browsing, all-empty manual selection and callback freshness as availability changes.
- Stock provider/security/server files are unchanged from 1.4.2. The original recovered source ZIP remains unchanged and separate.
- APK manifest/signature verified: `com.openticker.app.figmapreview`, versionCode 11, `1.5.1-figma-preview`, min SDK26, target SDK36. Signing certificate matches GUI Preview 1.5.0, so this is prepared as an update to that preview, not production OpenTicker.
- APK SHA-256: `3d30b639ae8f3b237603a85add4cb2b41c48b0ce77fdd0aa3c3e190c8c46f9fe`.

## Runtime limitation and install

A fresh official API30 x86_64 emulator was installed for visual QA, but software-only boot did not complete within the bounded attempt and adb became unresponsive. The emulator was stopped. No APK installation, native screenshot, navigation or physical-tablet rendering was verified. Compilation, tests and static layout review are complete; pixel-perfect/device behavior is not certified.

Install this APK over **OpenTicker GUI Preview**. Its package and signer match 1.5.0 and its version code increases to 11. Existing preview settings are retained; fresh installations use Philadelphia. Production OpenTicker remains separate. Do not uninstall either app to work around any installer error. If a previously saved custom city remains, Settings can still change it normally.

No push, merge, deployment, live account or user-device settings changes were made. This source archive omits build caches, SDKs, emulator files, credentials and private signing keys.
