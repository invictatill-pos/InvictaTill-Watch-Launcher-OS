# HealthSync — read this first for future feature work

Snapshot date: 7 October 2026. Baseline: Watch 2.2.0 (code 10), Phone 2.1.0 (code 4).

This file accompanies a portable source archive. It records the user's goal, implemented behavior, architecture, verified results and remaining limits. It is a snapshot, not a promise that later source or device state is unchanged. Compare it with the included build files and the next user request before editing.

## How to resume

1. Read this file, `README.md`, `docs/LAUNCHER-ROADMAP.md` and `dist/BUILD-REPORT-Android8-2.2.0.md`.
2. Extract the `HealthSync/` directory into a working folder. Both applications' complete source/resources/tests and Gradle configuration are included. No earlier chat is needed to understand this baseline.
3. Identify the next requested feature, inspect the affected current source and implement it while preserving the existing shell, data and phone/watch protocol.
4. Build and verify the affected modules. For Bluetooth/protocol changes, review and test both apps and deliver compatible APKs together.
5. Update this handoff with the new versions, changes, validation and open issues, then create a fresh continuation archive. This export will not automatically learn changes made after its snapshot date.

Suggested next-chat message: “Continue HealthSync from this archive. Read HealthSync/READ_FIRST.md and inspect the source. Add this feature: [describe the feature]. Preserve existing features and data, test the change and export the updated project.”

## User intent and design direction

The user has an Android phone app and an Android watch app. The watch is described as a Kolabee U8 Ultra running ordinary Android rather than Wear OS; its exact firmware, display type and exposed vendor interfaces have not been confirmed on hardware.

The user wants a watch launcher with a complete watch OS feel and useful watch functions, plus reliable phone connectivity and fitness tracking. They rejected the earlier launcher that mostly opened separate menus. They gave discretion to improve the plan/design. The current solution is a cohesive watch Home shell with compact controls, gestures and real state. A Wear OS-style visual direction was used; a specific Apple Watch design was not confirmed.

Preserve usable visible routes as well as gestures. Prefer a compact native watch surface over a long menu for everyday actions. Keep setup, recovery and detailed forms accessible. Report firmware limits and missing sensor data honestly; do not claim an APK can replace the OS or obtain system privileges by becoming Home.

## Current baseline and delivery

| Application | Package | Version / code | SDK | Delivered build |
| --- | --- | --- | --- | --- |
| Watch | com.healthsync.watch | 2.2.0 / 10 | min 23, target/compile 34 | Debug, unminified, v1/v2 signatures |
| Phone | com.healthsync.phone | 2.1.0 / 4 | min 26, target/compile 34 | Release, R8 minified, v2 signature |

Current APKs included under `dist/`:

- `HealthSync-Watch-2.2.0-Android8.apk`, SHA-256 `5634b47a43f20083bf5b5e927e83bc0401578632373018edef24e205208eb23a`.
- `HealthSync-Phone-2.1.0.apk`, SHA-256 `d44f8711a06d8b6c0383c78c1acf491724f6013d9704b531ba9a13fc2d8c72e2`.

Both use the existing development certificate, SHA-256 `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`. Keep package IDs and increment version codes for updates. Both release configurations currently use debug signing. The original private debug keystore is outside the project and is intentionally absent from this archive. On a different machine, default debug signing creates a different certificate, so its APKs cannot update the existing installed apps. Reuse the original signing key through the user's separate trusted setup before delivering an upgrade; do not suggest uninstalling without explaining data loss. Store distribution needs a private production signing plan.

This ZIP contains source/context and installable baseline APKs. It does not back up the user's health history, device app data, permissions or Bluetooth pairing.

## Implemented watch experience

| Area | Current behavior |
| --- | --- |
| Clock/Home | Orbit, Analog, Minimal, Health rings, Classic and Dashboard faces. Real battery, phone connection, steps, fresh heart rate and goal progress; notification badge; active workout/timer glances. |
| Navigation | Swipe up Apps, down Controls, left Notifications, right Fitness. Panels follow the finger and commit by distance/velocity. Left-edge/hardware Back unwind history and close detail sheets first. Home returns to the clock. Visible Apps/Notifications/Settings dock. Dashboard retains central vertical scrolling. |
| Apps | Actual installed launcher components/icons; All, Pins and Recent; native Health/Music/Notifications/Tools/Settings routes; pin/unpin and app information; rotary scrolling. Font-aware two/three-column layout. |
| Controls | Actual Wi-Fi/Bluetooth/DND/battery/airplane/saver state; direct legacy toggles only where allowed; guarded settings elsewhere. Sound sliders, per-window brightness, white-screen flashlight, AOD and Settings/Tools routes. |
| Notifications | Persistent Phone/Watch cards, unread filtering, saved offline details and bounded retention. Native watch capture uses a separate watch Notification Access grant. Native Open/Dismiss targets the matching active notification. Remote replies are phone-only and require the original freeform reply action and connection. |
| Fitness | Step-goal ring, fresh/available heart rate, saved daily totals, measurements, active workout and history. Eight modes: Walk, Run, Cycling, Basketball, Home workout, Badminton, Cricket, Yoga. |
| Phone controls | Confirmed track/artist/playback, supported media actions, phone battery and Find phone. Offline/pending/unavailable states are explicit. Finder rings for up to 15 seconds, supports Stop on both devices, respects sound policy and checks notification access/channel. |
| Tools | Persisted countdown with pause/resume/cancel, exact or visibly approximate scheduling, completion notification/Stop and Home glance. Persisted stopwatch/laps. Alarm opens an installed Android alarm handler. |
| Display | AOD is opt-in and keeps the foreground clock awake, dims after 15 seconds, uses black/minute updates/pixel shifting and consumes the first wake touch. Optional wrist brightening samples non-wakeup motion sensors only while dim/foreground. Wallpaper uses the system picker/backdrop where provided. |
| Recovery | Optional Home selection/restoration, another Home launcher, Android settings, permissions, battery settings and actual device/sensor report. Clock, local tools and saved inbox work offline. |

The phone app retains onboarding/permissions, connection controls, fitness dashboard/history/charts, workout details/search/export, notification history/filters and profile/goal settings. Calls and watch workout synchronization remain part of the existing paired-app feature set.

## Architecture and source map

Paths below are relative to the extracted `HealthSync/` root.

| Concern | Primary source |
| --- | --- |
| Watch Home and lifecycle/gestures | `WatchApp/app/src/main/java/com/healthsync/watch/ui/WatchFaceActivity.kt`, `LauncherGesturePolicy.kt`, `WatchDisplayPolicy.kt`; layout `res/layout/activity_watch_face.xml` |
| Embedded watch surfaces | Watch `ui/shell/`: `AppGridPanel`, `QuickControlsPanel`, `NotificationsPanel`, `FitnessPanel`, `MediaPanel`, `FacesPanel`, `WatchSettingsPanel`, `ShellNavigation` |
| Clock and dock drawing | Watch `ui/shell/OrbitWatchFaceView.kt`, `WatchDockButton.kt`; legacy face views under `ui/` |
| System controls and brightness | Watch `ui/shell/WatchSystemControls.kt`, `ui/launcher/LauncherPanelActivity.kt` and its `LauncherBrightness` object; `QuickSettingsActivity` retains Home/device/recovery pages |
| Detailed settings | Watch `ui/WatchOptionsActivity.kt`, `data/WatchPreferences.kt` |
| Wrist brightening | Watch `ui/shell/WristWakeController.kt`, `WristRaiseDetector.kt` |
| Watch notification persistence/UI | Watch `notification/NotificationInboxStore.kt`, `LocalNotificationListenerService.kt`, `NotificationDisplayActivity.kt`, `NotificationsInboxActivity.kt` |
| Timer/stopwatch | Watch `timer/WatchTimeModels.kt`, `WatchTimerStore.kt`, `WatchTimerScheduler.kt`, `service/WatchTimerService.kt`, `receiver/WatchTimerReceiver.kt`, `ui/shell/WatchUtilitiesActivity.kt` |
| Bluetooth transport | Watch `service/BluetoothClientService.kt`; phone `service/BluetoothSyncService.kt`; each app's `data/JsonLineReader.kt` |
| Shared wire model equivalents | Watch `data/SyncModels.kt`; phone `data/model/Models.kt` |
| Remote phone controls | Watch `ui/shell/PhoneControlsBridge.kt`; phone `service/PhoneRemoteControls.kt` |
| Phone notification/call access | Phone `service/HealthNotificationListenerService.kt`, `CallMonitorService.kt` |
| Watch tracking/health | Watch `service/SensorCollectorService.kt`, `WorkoutTrackingService.kt`, `sensor/`, `algorithm/`, workout Activities and `ui/WorkoutHistoryStore.kt` |
| Watch/phone health storage | Watch `data/WatchDatabaseHelper.kt`; phone Room `data/FitnessDatabase.kt`, `FitnessRepository.kt` |
| Phone UI | Phone `MainActivity.kt`, `ui/`, `viewmodel/`; Compose/Hilt/Room |
| Platform permissions and components | Both `app/src/main/AndroidManifest.xml` files |
| Tests | Both `app/src/test/` trees |

Watch uses Kotlin, AppCompat/Views/XML and Canvas. Phone uses Kotlin, Compose, Hilt, Room and DataStore. Extend these foundations instead of introducing a replacement launcher framework or rewriting persistence for a small feature.

### Integration rules worth preserving

- Home surfaces expose view/resume/pause/destroy callbacks. Pause polling/receivers when hidden, backgrounded or screen-off; stop the clock's one-second redraws while a panel is visible. Surface detail Back must run before panel Back. Keep app-grid `onNavigate` connected to the shell.
- Do not steal scrolling or app long-presses from active panels. Face gestures and long-press run on the clock; panel Back starts at the left edge. Short/cancelled and multi-pointer swipes must not activate underlying buttons.
- `WatchFaceActivity` intentionally calls `super.onCreate(null)` to avoid restoring an obsolete Wear ambient fragment from old builds. Its shell history is restored separately. Do not reintroduce a Google Wear shared-library requirement on ordinary Android watches.
- Foreground dim-clock screen retention and brightness must clear on leaving the app. Flashlight saves/restores brightness and KEEP_SCREEN_ON. Wrist sampling is opt-in and stops while bright, in panels, background or screen-off; it never wakes a physically sleeping display.
- Wire messages are bounded newline-delimited UTF-8 JSON over paired Bluetooth Classic RFCOMM. Both services use UUID `fa87c0d0-afac-11de-8a39-0800200c9a66`. Maintain both model copies and both handlers when adding commands. Existing features rely on serialized writes, reconnect/backoff and acknowledgements after persistence.
- `PHONE_CONTROL` / `PHONE_CONTROL_STATE` are the new media/finder protocol. Older phone builds time out honestly. Phone 2.1.0 is needed for new controls. Do not report success merely because a request was queued.
- Media access depends on phone Notification Access and a supported active session. Find phone checks posting/channel permission, uses a bounded 17-second partial wake lock for its 15-second stop, and releases it on Stop/error/disconnect/destroy. Do not change DND or volume automatically.
- Phone and Watch notification identities must stay isolated. Inbox schema 2 migrates schema-1 rows to source `phone` while retaining their old identity hashes. Native Open/Dismiss checks exact current key/package/post time/content; a stale saved row must not act on a newer notification. Replies must never cross to a Watch entry.
- Timer/stopwatch models use elapsedRealtime, persisted anchors and reboot detection. Exact alarm access has an approximate fallback. Watch timer FGS uses specialUse on modern Android. Guard newer APIs against minSdk 23; e.g. countdown chronometer/requestRebind require API 24. Never hold a CPU wake lock for a whole countdown.
- Preserve saved workouts, pending sync acknowledgements and migrations. Watch health database is version 2; watch inbox is version 2; phone Room database is version 5. Phone `AppModule` registers explicit migrations and also retains `fallbackToDestructiveMigration()` for unsupported paths: inspect this before changing the schema and add the correct migration rather than relying on a reset. Do not synthesize heart/oxygen/sleep readings.

## Build and package

Original workspace was `C:\Users\ashis\Desktop\Personal Keep\Health App`; it had no Git repository or applicable AGENTS.md. The export has no Git history. A future workspace can be elsewhere.

Build baseline: Gradle 8.7 wrapper, AGP 8.5.0, Kotlin 2.0.0. Dependency versions are pinned in `gradle/libs.versions.toml` and module files. Use JDK 17+ and Android SDK platform/build-tools 34. The Windows wrapper honors JAVA_HOME or finds Android Studio's bundled JBR. Watch bytecode target is Java 11; phone target is Java 17.

Create a local `local.properties` with your actual SDK directory. The original machine used `C:/Users/ashis/AppData/Local/Android/Sdk`; this machine-specific configuration is omitted. Dependency downloads need Google Maven, Maven Central, Gradle distribution access and JitPack. SDK/runtime/caches are not bundled. Only `gradlew.bat` exists in this baseline; non-Windows environments need an appropriate Gradle 8.7 entry point.

From the extracted project root in PowerShell:

```powershell
.\gradlew.bat :WatchApp:app:testDebugUnitTest :PhoneApp:app:testDebugUnitTest :WatchApp:app:lintDebug :PhoneApp:app:lintDebug
.\gradlew.bat :WatchApp:app:assembleDebug :PhoneApp:app:assembleRelease
.\tools\update_dist.ps1 -WatchVersion '2.2.0' -PhoneVersion '2.1.0'
```

Outputs: `WatchApp/app/build/outputs/apk/debug/app-debug.apk` and `PhoneApp/app/build/outputs/apk/release/app-release.apk`. Packaging checks module metadata, copies current APKs, writes checksums and creates the two-APK installation bundle. Update version parameters, defaults, install instructions, build report and handoff for the next delivery. `Install-Watch-Diagnostic.ps1` requires an explicit device serial, preserves app data and does not automatically choose Home.

## Verified baseline

These are historical results for the included 2.2.0/2.1.0 snapshot, not validation of a future change.

| Module | JVM tests | Failed / errors / skipped | Lint errors | Lint warnings |
| --- | ---: | --- | ---: | ---: |
| Watch | 52 | 0 / 0 / 0 | 0 | 315 |
| Phone | 14 | 0 / 0 / 0 | 0 | 11 |

Both assemblies, signature verification and 4-byte ZIP alignment passed. All 66 tests passed. Warnings remain, mostly localization/text, compatibility/deprecation and layout concerns.

An isolated Android 8/API 26 x86 emulator (`emulator-5580`) validated Home/navigation, app-options edge Back, actual native alert capture/Open/Dismiss, an exact 15-second timer while power state was Asleep, timer/stopwatch retention through APK upgrades and flashlight display restoration. The minified phone APK opened onboarding. Logical 400×400 and 320×320 at font scale 1.3 were inspected; smaller/larger-font grids were corrected. Ambient wake check recorded first touch stays on Clock, second touch opens Fitness. No app AndroidRuntime crash was recorded.

The test emulator was stopped and stock Launcher3 Home and display/font settings were restored. No physical phone/watch was connected. Selected reports/screenshots accompany this archive. Screenshots are actual emulator captures from the 2.2.0 QA run; not all are taken after every final minor layout adjustment. The original isolated emulator/system image and device data are not included.

## Limits and outstanding device acceptance

- Actual Kolabee model/build/API, circular safe area, screen type, buttons/crown, Bluetooth Classic support and exposed sensors need device inspection. Do not assert Android 9/OLED/ClockSkin support from the product name.
- Factory low-power AOD, screen-off wrist wake, secure-lock behavior, power-button interception and proprietary factory face engines require verified firmware/vendor support. Current dim clock is foreground simulation; current wrist option only brightens that visible dim clock.
- Real phone pairing/reconnect, media/finder sound and timeout, phone replies, calls/audio routing, GPS/vendor sensors, real reboot/Doze timer precision, physical wrist orientation and overnight battery/temperature remain unverified.
- Android controls depend on OS/API and permission grants. Wi-Fi direct changes are restricted on Android 10+, Bluetooth on Android 13+, DND needs policy access; phone/watch notification listeners have separate grants. Keep guarded system routes and useful unavailable states.
- Home is not immune to process death, force-stop or OEM cleaners. Preserve recovery behavior and distinguish process recreation from deliberate force-stop. Do not promise universal battery life or continuous background sensors without device evidence.
- Stock/vendor face importing is not implemented. Get actual permitted samples and validate format/license/security before adding it. System wallpaper does not embed a vendor face engine.

No next feature has been chosen. The next user message supplies that scope. Use `docs/LAUNCHER-ROADMAP.md` for the hardware acceptance checklist and update this snapshot after completing that work.

## Patch note — 8 October 2026: heart-rate accuracy feedback

The historical 2.2.0/2.1.0 snapshot above remains unchanged. The current patch targets Watch 2.3.3 (code 14) and Phone 2.2.3 (code 8). The user supplied a diagnostic from a U8 Ultra running Android 8.1/API 27: BODY_SENSORS was granted, one TYHX hrs3918 heart-rate sensor registered, 2,904 frames arrived with accuracy 0 and none were accepted. The same attempt was still measuring 116 seconds after acquisition began. The user confirmed that the original/vendor app displays heart rate. This Android sensor report contained no raw BPM values, so it did not establish whether this stream supplied valid in-range heart-rate values.

Fresh in-range frames marked accuracy 0 can now appear as explicitly **unverified** sensor previews in watch Health/Fitness and the connected phone Dashboard. This display path stays separate from reliable measurements: previews are memory-only, expire after two minutes, clear from the phone on disconnect and never enter health history, workout samples or watch faces. Reliable acceptance still requires accuracy 1..3, 25..240 BPM and a fresh, ordered capture timestamp from the current acquisition. No-contact, invalid-value and invalid-timestamp frames cannot produce a preview.

Heart-rate windows retain their original 60-second acquisition deadline even when Measure is tapped repeatedly; a new attempt starts after the prior listener cleanup. Independent value and timestamp diagnostic counters expose problems hidden by the earlier accuracy-first rejection path, while the report continues to exclude raw BPM values. Existing permission recovery, preferred-sensor selection, launcher features and saved data are preserved.

Current installation instructions are in `dist/ANDROID8-INSTALL.md`; packaging defaults in `tools/update_dist.ps1` target these versions. Refer to `dist/BUILD-REPORT-Android8-2.3.3.md` and `validation/heart-rate-preview-2.3.3/` for the actual build, unit-test, lint and runtime evidence. Validation covers software paths; no physical watch or phone was connected for this patch. These changes do not establish that this Android sensor stream produces reliable measurements; repeat Measure on the updated watch and inspect the details and fresh diagnostic to confirm its values, accuracy and timestamps.

## Patch note — 8 October 2026: heart rate on watch faces and timed readings

The historical snapshots above remain unchanged. The latest patch targets Watch 2.3.4 (code 15) and Phone 2.2.4 (code 9). The user confirmed that Watch 2.3.3 displays BPM from the sensor preview on their actual U8 Ultra, but the watch-face heart-rate fields stayed blank. They also requested removal of the unverified/unreliable label from the everyday display.

Fresh accuracy-0 sensor previews now reach all existing heart-rate complications as ordinary BPM values. Some clean and analog faces intentionally omit heart rate; this patch does not add heart-rate fields to them. Health, Fitness and the phone Dashboard use **Sensor reading** on their main cards. Technical accuracy status and rejection counts remain available in heart-rate details and the copied diagnostic report.

Sensor display feedback remains separate from reliable measurements and can now stay visible with its age for up to 24 hours, matching reliable face/Health retention. The watch persists the last feedback snapshot in separate preferences (`last_sensor_hr_bpm` and `last_sensor_hr_time`), never as a health-history row. The phone keeps feedback in memory, clears it on disconnect and retrieves the watch's last reading with its original capture time on reconnect. Workout statistics still require a reliable reading no more than two minutes old. Reliability acceptance, value ranges, capture timestamp checks, the 60-second acquisition deadline and existing saved data are preserved.

The user then reported the optical heart-rate sensor staying on and draining the battery. Heart-rate acquisition now unregisters immediately after the first valid accuracy-0 sensor reading or accepted reliable measurement and releases only that acquisition's wake-lock budget; another active measurement can continue. The first automatic acquisition waits for the configured interval. Manual Measure rebases the next automatic deadline, unsuccessful acquisitions time out, and missed slots do not accumulate a backlog. Off stops automatic acquisition but keeps explicit manual requests available. Workouts use the configured schedule instead of requesting extra heart-rate measurements every 15 seconds. Reconnects and unrelated settings preserve an unchanged interval's due time. These are software changes; no physical battery savings have been measured.

Scheduled measurements use elapsed-realtime wakeup alarms without a permanent measurement wake lock between samples. Exact alarms are used where permitted, with an approximate allow-while-idle fallback when exact-alarm access is unavailable. Alarm interval/revision/due-time tokens reject stale delivery after settings changes. Android Doze can delay the selected interval in deep idle; short selected periods do not guarantee equally frequent wakeups while the watch sleeps deeply.

Sensor intervals now sync in both directions without a database migration. Each app stores an atomic HR/SpO2 snapshot plus a `sensor_settings_revision`. Existing `WatchSettingsPayload` adds optional `sensorSettingsRevision = 0L`; the new `SENSOR_INTERVALS` message requires numeric integer `hrIntervalMs`, `spo2IntervalMs` and `revision`. Higher revisions win; equal revisions must carry the same values. A revision-0 watch snapshot establishes the legacy phone baseline only while the phone also has revision 0, so an old automatic phone reconnect cannot overwrite the watch's stored choice. Local value changes increment the revision; explicit phone Apply increments even for unchanged choices, while automatic reconnect never increments. Ordering uses device wall clocks and assumes they are correct; `max(previous + 1, currentTimeMillis)` protects local monotonicity if that device's clock moves backward. Both pickers now offer 30 seconds, 1/5/10/30 minutes, 1 hour and Off. `BluetoothClientService.syncSensorIntervals(context)` sends the saved watch snapshot without creating a new edit.

Current install instructions are in `dist/ANDROID8-INSTALL.md`, and `tools/update_dist.ps1` defaults to Watch 2.3.4 / Phone 2.2.4. Refer to `dist/BUILD-REPORT-Android8-2.3.4.md` and `validation/heart-rate-faces-2.3.4/` for the actual build, unit-test, lint and runtime evidence. The user's successful 2.3.3 preview is device feedback, not a physical test of the new patch. No physical watch or phone was connected during 2.3.4 validation; the new face display and main-card labels still need confirmation on the user's watch and phone.
