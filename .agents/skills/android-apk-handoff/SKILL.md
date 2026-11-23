---
name: android-apk-handoff
description: Build or hand off a OpenTicker test APK, identify the exact revision and variant, and report the Android CI and device evidence.
---

# Hand off an OpenTicker APK

1. Start from the requested revision in `.github/workflows/android-apk.yml`. Record the PR head and tested checkout if CI tested a merge ref.
2. Follow `run-the-tests` for the host checks and both Gradle variants. For a UI handoff, use `tablet-ui-review`; for market-data changes use `market-data-verification`.
3. Choose the matching artifact from the same run. **OpenTicker-debug-apk** is the standard `com.openticker.app` package. **OpenTicker-gui-preview-apk** is `com.openticker.app.figmapreview` and has separate preferences. Link the run and artifact, and include the corresponding SHA-256 from the run summary. Check signer compatibility before suggesting an update over saved settings.
4. Include the unit/lint reports, screenshot artifact or device result for the changed behavior, and any outstanding tablet/live-data checks. Deliver a draft PR and leave merging to the owner.
