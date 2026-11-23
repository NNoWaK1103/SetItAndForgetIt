---
name: tablet-ui-review
description: Review OpenTicker's native Philadelphia dashboard, market charts and bottom wires on a phone or tablet after a Compose or Figma change.
---

# Review the OpenTicker dashboard

Use the supplied Figma frame or screenshot first. The checked-in Philadelphia reference and its design decisions are in `docs/PHILADELPHIA_PREVIEW.md`. Map the frame to existing Compose components, Barlow Condensed, the Philadelphia clock and both bottom wires. Use Google's adaptive or Compose performance skill when the task involves breakpoints or jank.

Capture landscape at 1280×800 and a narrower window with the same feed state. Check long team and stock names, chart range controls, stale/unavailable labels, header navigation, wire clipping and visibility at a glance. Pause the slideshow for comparable frames and verify it can resume. Use actual data states or identified fixtures.

Run `.maestro/stock-dashboard.yaml` against the isolated preview and use the existing Android tablet preview workflow for broader screenshots and playback. Report the APK variant, API, dimensions, commit, actual screenshots and concrete discrepancies. Distinguish emulator observations from physical-tablet results.
