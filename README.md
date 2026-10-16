# HealthSync 2.9.4

Phone 2.9.4 adds a visible **Bluetooth tethering** button under **Device → Internet for watch apps**. One tap opens Android's tethering controls where supported; turn on Bluetooth tethering there. Watch 2.9.3 remains current and compatible. See the [Bluetooth internet guide](docs/BLUETOOTH-INTERNET.md) for setup, fallback settings screens, and device checks.

HealthSync is an Android watch launcher and Android phone companion with sensor capture, local workout history, Bluetooth sync, notifications, calls, watch faces, and watch utilities. The 13 October 2026 Face Studio update adds blank projects, custom text and shape layers, direct canvas editing, precise styling, undo/redo, draft recovery, portable project import/export, ambient controls, and design testing. See the [Face Studio guide](docs/FACE-STUDIO.md).

## Install or update

| Device | APK | App version | Android version | Build code |
| --- | --- | --- | --- | --- |
| Watch | [HealthSync-Watch.apk](dist/HealthSync-Watch.apk) | 2.9.3 | Android 6 / API 23 or newer | 37 |
| Phone | [HealthSync-Phone.apk](dist/HealthSync-Phone.apk) | 2.9.4 | Android 8 / API 26 or newer | 32 |

Install both apps over the existing packages. The apps retain the existing development signing certificate. The phone APK is an optimized release build; the watch APK retains the compatible v1/v2-signed debug format used by earlier watch deliveries. The [installation guide](dist/ANDROID8-INSTALL.md) explains Bluetooth pairing, permissions, updates, and Home recovery. Published APKs are available in [GitHub Releases](https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases).

HealthSync 2.7.0 removes unverifiable oxygen and sleep records from active history and retains them in a local unverified archive. Saved workouts, step history, and accepted heart-rate records remain. The unverifiable old watch live step cache is archived and restarted; stored historical steps remain. A missing reading stays empty until a real capture is available.

Phone database versions 4 and 5 retain verified history during the upgrade. Earlier database versions 1–3 have no documented schema: their original tables are preserved locally as `legacy_v1_*`, `legacy_v2_*`, or `legacy_v3_*` archives, and active history starts empty. The app does not guess the meaning or capture source of those older records. Destructive migration fallback is disabled.

## Health capture

| Feature | Source and behavior |
| --- | --- |
| Heart rate | A compatible Android/vendor BPM sensor, then readings shared by installed watch health apps when capture fails. Imports retain app source and original time. Accuracy-zero sensor feedback is explicitly unverified and excluded from history/workout statistics. Raw PPG and heartbeat-event sensors are not treated as BPM. |
| Blood oxygen | An explicit vendor saturation sensor, then exported app saturation records. Manual and scheduled acquisition share the same flow. Unverified sensor feedback is separate from accepted captures. Private app data remains inaccessible without an export. |
| Steps | One active hardware counter/detector source, or a motion-based step estimate when needed. Invalid or reordered sensor timestamps are rejected. Missing permission/sensor states are visible. |
| Rest | A motion-based estimate from continuously observed overnight accelerometer samples. No first-run night, deep/light/REM stages, or sleep score is generated. Rest is an estimate, not proof of sleep. |
| Stress / HRV | Unavailable without a validated measurement source. Guided box breathing is available without inventing a score or claiming measured improvement. |
| Water | A manual daily log, with undo, configurable goal, and midnight reset. It does not claim sensor capture or phone synchronization. |
| Workout distance and calories | Valid location distance is identified separately from stride-based distance estimates. Calories remain model estimates. Heart-rate graphs use captured samples in the workout interval; no distribution is synthesized from averages. |
| Activity reminders | Based on observed steps and monitoring coverage; no standing-posture sensor is claimed. |
| Compass / elevation | Actual orientation sensors, magnetic-heading/calibration status, and fresh valid location altitude. Missing signals do not display default north or zero elevation. |

See the [health data contract](docs/HEALTH-DATA-CONTRACT.md) for validation rules, upgrade cleanup, and device acceptance checks.

The [U8 Ultra fallback guide](docs/WATCH-HEALTH-FALLBACK.md) explains **Open watch health app**, **Check watch app data**, exported provider compatibility, and workout location recovery. GPS/network/OEM tracking accepts fresh passive locations shared by other watch apps and restarts stalled subscriptions. Physical watch sensor/export availability remains subject to the firmware.

## Navigation and features

The phone keeps five primary destinations: Today, Training, Faces, Device, and Settings. Notification history and source filters are reachable from Today and Settings with a visible Back route. Workout details have loading/unavailable states and return navigation. Watch-face creation and installation remain accessible from Faces. Shared cream/forest colors, headings, card spacing, and readable controls tie the screens together.

On the watch, swipe up from the clock for Apps, down for Controls, left for Notifications, and right for Fitness. Hold the clock to pick a face. Back returns through launcher panels; Clock/Home returns to the clock. Health screens have visible Back and Clock controls and scroll inside a round-screen-safe layout.

The app grid and Fitness panel provide direct routes to heart rate, oxygen, rest, breathing, hydration, workouts/history, compass, music, notifications, tools, and settings. The five built-in faces are Orbit Pro, Chrono Ultra, Retro Illuminator, Cyberpunk HUD, and Bauhaus Minimal; additional designs can be installed from the phone. Always-on display, foreground wrist raise, software bezel, timer, stopwatch, alarms, and Home recovery remain available where Android and firmware support them.

The [communication guide](docs/COMMUNICATION-SETUP.md) covers notification access, caller identity, companion watch association, call controls, Bluetooth call audio, reply results, media, and Find phone. Audio, sensors, GPS, and system controls depend on exposed hardware and Android permissions.

## App updates

Phone Settings and Device can check for and send watch updates. The watch also offers a direct Wi-Fi update route. Downloads require complete version metadata, HTTPS, a matching SHA-256 checksum, and the expected package/version. Installation on each device also checks compatibility with its installed signing certificate. Interrupted/invalid transfers cannot be installed.

The phone distinguishes sending the watch package from watch verification and Android installation. Watches running 2.7.0 or later return a correlated verification result. Earlier watches require checking their update notification. Android still asks the device user to authorize installation; sending a package does not silently install it.

Release URLs are pinned to a version tag. `tools/update_dist.ps1` produces aliases, versioned APKs, checksums, the installation bundle, and manifest metadata from the exact built artifacts.

## Build and verification

Use JDK 17 or newer and Android SDK platform/build-tools 34. Set `sdk.dir` in local.properties. The wrapper uses `JAVA_HOME` or Android Studio's bundled runtime.

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug
.\gradlew.bat :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug
.\tools\update_dist.ps1 -WatchVersion 2.9.3 -PhoneVersion 2.9.4 -ReleaseVersion 2.9.4
```

The [2.9.4 build report](dist/BUILD-REPORT-Android8-2.9.4.md) records validation. No physical watch or phone was connected during this update. Automated tests and static checks do not establish physical sensor accuracy, GPS reception, overnight battery use, circular-display fit, call audio, or OEM permission behavior; these require device checks.
