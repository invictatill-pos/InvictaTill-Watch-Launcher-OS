# HealthSync 2.8.4 build report

Prepared 13 October 2026 for the Kolabee U8 Ultra Multi-Tier Battery & Power Saver System, Hardware Telemetry, Two-Way Hydration, and Sensor Sync Release.

| Package | Variant | Version | Build code | Minimum Android |
| --- | --- | --- | --- | --- |
| `com.healthsync.watch` | Debug, v1/v2 signed | 2.8.4 | 32 | 6 / API 23 |
| `com.healthsync.phone` | Optimized release, v2 signed | 2.8.4 | 26 | 8 / API 26 |

Both packages use the existing Android development certificate. Signature verification passed. Certificate SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

## Changes

- **Live Watch Battery & Extended Hardware Telemetry**:
  - Implemented `WatchDeviceInfoCollector` capturing exact battery levels and charging states using Android's sticky `ACTION_BATTERY_CHANGED` intent filter (eliminating `-1` battery readings from custom watch HALs).
  - Added full hardware telemetry extraction: available & total internal storage (`StatFs`), power saver state, Android OS version, manufacturer, and detected hardware sensor flags (Heart Rate PPG, SpO₂ Optical, Pedometer).
  - Telemetry is transmitted automatically upon Bluetooth connection, during battery changes, and on-demand via the new `FETCH_DEVICE_INFO` protocol message.
  - Rendered a dedicated **"Watch Hardware & Telemetry"** card and live charging indicator badge on the companion Phone App (`DeviceScreen`) with a **"Refresh"** action button.

- **Two-Way Hydration Tracking & Synchronization**:
  - Implemented bidirectional hydration logging between Watch and Phone.
  - **Phone App**: Added interactive **HydrationCard** to `DashboardScreen` displaying current intake, daily goal, progress bar, and quick-log buttons (`+250 ml`, `+500 ml`).
  - **Watch App**: Added Bluetooth synchronization to `HydrationActivity` so logging water or adjusting goals on the watch instantly synchronizes to the phone, and phone logging broadcasts to the watch UI.
  - Added Room database `HydrationEntity`, `HydrationDao`, and non-destructive migration (`migration7To8`).

- **Heart Rate & SpO₂ Offline Persistence & Batch Sync**:
  - Created `TABLE_HEART_RATE` and `TABLE_SPO2` in `WatchDatabaseHelper` (DB version 4).
  - Persisted all sensor-captured and companion-imported readings to SQLite on the watch, enabling offline logging.
  - Enhanced `BatchedSyncPayload` and `SyncAckPayload` to transmit and acknowledge offline heart rate and SpO₂ readings with timestamp confirmation upon reconnection.

- **Wellness Settings**:
  - Added configurable Daily Water Goal, Hydration Reminder, and Stand / Movement Reminder controls in `SettingsScreen` and synchronized them to the watch via `WELLNESS_SETTINGS`.

- **Multi-Tier Power Saving System**:
  - Normal Mode, Extreme Power Saver Mode (Stamina with active BT communication), and Super Extreme Power Saver Mode (Emergency standby with RF radio shutoff and emergency digital dial).

## Validation completed

| Check | Result |
| --- | --- |
| Watch unit tests (`:WatchApp:app:testDebugUnitTest`) | 225 tests passed, 0 failures |
| Phone unit tests (`:PhoneApp:app:testDebugUnitTest`) | 104 tests passed, 0 failures |
| Protocol parity | 1-to-1 matching `MessageType`, `DeviceInfoPayload`, `BatchedSyncPayload`, `HydrationPayload`, `WellnessSettingsPayload` |
| Watch debug APK | Built successfully (`HealthSync-Watch-2.8.4-Android8.apk`) |
| Phone release APK | Built successfully (`HealthSync-Phone-2.8.4.apk`) |
| Distribution bundle | Packaged with SHA256 checksums in `dist/` |

Commands:

```powershell
.\gradlew.bat test
.\gradlew.bat :WatchApp:app:assembleDebug :PhoneApp:app:assembleRelease
.\tools\update_dist.ps1 -WatchVersion 2.8.4 -PhoneVersion 2.8.4
```
