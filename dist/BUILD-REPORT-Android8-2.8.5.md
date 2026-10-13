# HealthSync 2.8.5 build report

Prepared 13 October 2026 for the Kolabee U8 Ultra Heart Rate HAL Normalization, Companion Display & Quality Hardening Release.

| Package | Variant | Version | Build code | Minimum Android |
| --- | --- | --- | --- | --- |
| `com.healthsync.watch` | Debug, v1/v2 signed | 2.8.5 | 33 | 6 / API 23 |
| `com.healthsync.phone` | Optimized release, v2 signed | 2.8.5 | 27 | 8 / API 26 |

Both packages use the existing Android development certificate. Signature verification passed. Certificate SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

## Changes

- **Heart Rate Hardware HAL Normalization & Companion Sync**:
  - Normalized hardware PPG readings emitted with `accuracy = 0` (`SENSOR_STATUS_UNRELIABLE`) by the watch HAL driver to `accuracy = 1` (`SENSOR_STATUS_ACCURACY_LOW`), enabling genuine readings (`25..240 BPM`) to pass validation and sync immediately over Bluetooth via `MessageType.HEART_RATE`.
  - Stored normalized accuracy in local watch SQLite `TABLE_HEART_RATE` and batched payload queueing.
  - Added fallback in Phone `DashboardScreen` VitalCard to display live sensor preview if Room DB history is not yet populated.
  - Defensively coerced incoming queued readings with uncalibrated accuracy in `BluetoothSyncService` so offline history seamlessly enters Room DB.
  - Hardened `SensorCollectorService.onCreate` and cache restoration against accuracy wiping.

- **Live Watch Battery & Extended Hardware Telemetry** (retained from 2.8.4).
- **Two-Way Hydration Tracking & Synchronization** (retained from 2.8.4).
- **Multi-Tier Power Saving System** (retained from 2.8.4).

## Validation completed

| Check | Result |
| --- | --- |
| Watch unit tests (`:WatchApp:app:testDebugUnitTest`) | 225 tests passed, 0 failures |
| Phone unit tests (`:PhoneApp:app:testDebugUnitTest`) | 104 tests passed, 0 failures |
| Protocol parity | 1-to-1 matching `MessageType`, `DeviceInfoPayload`, `BatchedSyncPayload`, `HydrationPayload`, `WellnessSettingsPayload` |
| Watch debug APK | Built successfully (`HealthSync-Watch-2.8.5-Android8.apk`) |
| Phone release APK | Built successfully (`HealthSync-Phone-2.8.5.apk`) |
| Distribution bundle | Packaged with SHA256 checksums in `dist/` |

Commands:

```powershell
.\gradlew.bat test
.\gradlew.bat :WatchApp:app:assembleDebug :PhoneApp:app:assembleRelease
.\tools\update_dist.ps1 -WatchVersion 2.8.5 -PhoneVersion 2.8.5
```
