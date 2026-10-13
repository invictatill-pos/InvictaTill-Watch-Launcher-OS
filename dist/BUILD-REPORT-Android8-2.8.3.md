# HealthSync 2.8.3 build report

Prepared 13 October 2026 for the Kolabee U8 Ultra Multi-Tier Battery & Power Saver System and Full QA Release.

| Package | Variant | Version | Build code | Minimum Android |
| --- | --- | --- | --- | --- |
| `com.healthsync.watch` | Debug, v1/v2 signed | 2.8.3 | 31 | 6 / API 23 |
| `com.healthsync.phone` | Optimized release, v2 signed | 2.8.3 | 25 | 8 / API 26 |

Both packages use the existing Android development certificate. Signature verification passed. Certificate SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

## Changes

- **Multi-Tier Power Saving System Hardening**:
  - Implemented and QA-hardened `PowerSaveManager` orchestrating three distinct performance & endurance tiers across the watch platform:
    1. **Normal Mode**: Full 60 FPS animations, regular health sensor polling intervals, active phone companion sync (~24 hrs stamina).
    2. **Extreme Power Saver Mode (Stamina)**:
       - Saves battery at an extreme level (~72 hrs / 3x longevity).
       - Throttles dials to 1 Hz static ticks, pauses continuous 60 FPS sweep animators in Chrono Ultra and particle shaders in dynamic faces.
       - Relaxes high-frequency optical sensors (optical HR and SpO₂ periodic polling paused; manual measure available on-demand).
       - Fully suppresses background wakeup alarms from waking the display.
       - Disables wallpaper backdrop for true OLED solid black (`#000000`) pixel power savings.
       - Disables wrist wake gesture to prevent accidental gyroscope wakeups.
       - **CRITICAL**: **Keeps Bluetooth communication with Phone App 100% alive** — incoming call alerts, phone notifications, and health sync continue without interruption.
    3. **Super Extreme Power Saver Mode (Emergency)**:
       - Maximum emergency endurance standby mode (~200+ hrs / 8+ days longevity).
       - **CRITICAL**: **Completely stops communication with Phone App** — powers off Bluetooth RF transceiver to drop radio current draw to 0 mA.
       - Suspends all background health sensor listeners and motion fall-detection polling.
       - Automatically terminates active workouts via `WorkoutTrackingService`.
       - Switches UI to a dedicated OLED-optimized high-contrast emergency clock display: large digital clock, date, battery endurance remaining, and an amber emergency badge.
       - Bypasses panel gesture swiping to preserve emergency stamina while providing a prominent **"Exit Super Saver"** button that immediately restores normal mode and reconnects to the phone.

- **QA & Reliability Hardening**:
  - **SensorCollectorService Lifecycle & Alarms**: Enforced battery saver checks across `onCreate`, `onStartCommand`, `ensureIntervalJobs`, `updateMeasurementAlarms`, and `handleScheduledAlarm` to prevent accidental sensor re-registration or alarm scheduling in stamina modes.
  - **Dynamic Watch Face ACK Resilience**: Handled install errors in `BluetoothClientService` to immediately send failure ACKs, avoiding 10-second client timeouts on malformed packages.
  - **Live Watch Battery Diagnostics**: Added real-time watch battery percentage display pill in companion phone `DeviceScreen` connection card (`🔋 Watch battery: XX%`).

## Validation completed

| Check | Result |
| --- | --- |
| Watch unit tests | All tests passed, zero failures/errors |
| Phone unit tests | All tests passed, zero failures/errors |
| PowerSaveManager unit tests | Verified tier transitions, communication boundary policy, and stamina math |
| Watch debug APK | Built successfully (`com.healthsync.watch` v2.8.3 code 31) |
| Phone release APK | Built successfully (`com.healthsync.phone` v2.8.3 code 25) |
| APK signatures | Verified; watch v1/v2, phone v2 |

Commands:

```powershell
.\gradlew.bat test
.\gradlew.bat :WatchApp:app:assembleDebug :PhoneApp:app:assembleRelease
.\tools\update_dist.ps1 -WatchVersion 2.8.3 -PhoneVersion 2.8.3
```
