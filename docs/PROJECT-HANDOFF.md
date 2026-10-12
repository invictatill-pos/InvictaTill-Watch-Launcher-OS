# HealthSync project handoff

Snapshot: 12 October 2026, Phone 2.7.0/code20 and Watch 2.7.0/code26. Read the current README, [health data contract](HEALTH-DATA-CONTRACT.md), [communication setup](COMMUNICATION-SETUP.md), and current build report before continuing. Older reports describe only their own version.

## User requirements

The user wants broad feature enhancement, usable navigation, an improved look throughout both apps, and updates delivered to users. All health data must originate in real captures or explicit manual entries; derived metrics must be labeled estimates. Missing hardware/data must produce an honest unavailable/empty state.

The watch is described as a Kolabee U8 Ultra on ordinary Android. Firmware sensor exposure, round-display dimensions, real battery behavior, and Bluetooth call audio remain unverified on hardware. HealthSync is an app/Home launcher; it does not replace firmware or gain private privileges.

## Architecture

- PhoneApp is Kotlin/Compose/Material3 with Hilt, Room, and a foreground Bluetooth RFCOMM service. Its primary routes are Dashboard, Workouts, Device, Faces, and Settings; notification history is a secondary route.
- WatchApp uses native Android views, a single-task Home clock shell, swipe panels, and Activities for detailed health/tools/workouts/settings. Shared round-safe UI is in LauncherPanelActivity and RoundScrollView.
- Both modules mirror JSON-line Bluetooth models. Add optional fields for compatible protocol evolution; review both serializers/validators, batch storage, acknowledgement, and UI when changing health records.
- SensorCollectorService validates original event time, sensor semantics, value, and accuracy. HeartRatePolicy/OxygenCapturePolicy/step guards prevent malformed or reinterpretation-based values entering history. Background body-sensor access is separate on Android13+.
- SleepTracker/ActigraphyRestEngine observe motion and save rest estimates; they do not infer sleep stages/quality. Health data provenance is mandatory for new oxygen/rest records.
- WorkoutTrackingService persists/checkpoints sessions; pause and route segmentation are meaningful. New sessions distinguish location distance from stride estimates. Calories remain estimates. WorkoutCaptureValidation and phone SyncValidation protect storage/sync.
- The phone Room database is version6 with explicit 4→5 and5→6 migrations. The watch workout SQLite database is version3. Current upgrades remove unverifiable old sleep/oxygen values while retaining genuine history.
- AppUpdateManager/WatchUpdateManager verify downloads and package identity/version/signing. Bluetooth transfer frames carry correlated transfer IDs; geometry, completion, hash, timeout, and cancellation are checked. Android installation consent remains on the receiving device.

## Build and delivery

Watch package: `com.healthsync.watch`, minAPI23, target/compile34, debug APK with v1/v2 signatures. Phone package: `com.healthsync.phone`, minAPI26, target/compile34, optimized release APK. Both retain the project's existing development signing certificate; do not change identity or certificate when upgrading users.

The original key stays outside the repository. Building elsewhere with a different default debug key cannot upgrade installed apps. Preserve the original signing setup; do not tell users to uninstall to bypass a certificate mismatch if history matters.

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug
.\tools\update_dist.ps1 -WatchVersion 2.7.0 -PhoneVersion 2.7.0
```

The packaging script derives version codes and hashes from output metadata/exact APKs, pins APK addresses to the release tag, and packages setup/health guides. Publish APK assets before activating root `version.json` so existing clients only see downloadable packages. Also upload `version.json` as a release asset for updater fallback.

## Required continuation checks

Never reintroduce default sleep, random oxygen, synthetic ECG/HR zones, stress/readiness scores without a measurement source, seeded face health values, or missing-data-as-success feedback. Design examples belong only in explicitly labeled face-design previews.

Preserve visible Back/Clock routes, circular-screen safe padding, sufficient touch targets/contrast, loading and empty states, Android permissions/settings recovery, offline history, pause/resume, and current navigation state. Avoid claiming a successful setting/reply/call/install merely because a command was queued.

Run the relevant regression suite plus both-module build/validation for protocol changes. Read actual report results; never copy a previous report's success claim. Physical health capture, circular layout, overnight tracking/battery, incoming alerts, calls/audio, OTA consent, and OEM background behavior still require actual devices.
