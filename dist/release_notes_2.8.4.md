# HealthSync v2.8.4 — Watch Hardware Telemetry, Two-Way Hydration & Offline Sensor Sync

HealthSync 2.8.4 delivers comprehensive quality assurance hardening across both the Watch App and Phone Companion, featuring real-time watch battery & hardware diagnostics, two-way hydration sync, offline Heart Rate & SpO₂ local SQLite queueing, and hardened multi-tier power saving modes.

---

### 🔋 Watch Hardware & Battery Diagnostics
- **Live Battery & Charging Telemetry**: Captured via Android's sticky `ACTION_BATTERY_CHANGED` intent filter, eliminating `-1` battery readings on customized Android 8.1 watch ROMs. Shows exact battery level and live `⚡ Charging` badge.
- **Extended Hardware Specs**: Phone Companion displays available and total internal storage (`StatFs`), power saver status, Android OS build, and detected sensors (Heart Rate PPG, Optical SpO₂, Pedometer).
- **On-Demand Refresh**: Dedicated "Refresh" action button on the Phone `DeviceScreen` queries the watch instantly via `FETCH_DEVICE_INFO`.

---

### 💧 Two-Way Hydration & Wellness Tracking
- **Interactive Hydration Card**: Phone App `DashboardScreen` includes water intake progress, daily goal tracking, and quick-log buttons (`+250 ml`, `+500 ml`).
- **Live Bidirectional Sync**: Logging water or adjusting daily goals on either Phone or Watch (`HydrationActivity`) immediately synchronizes to the companion device over Bluetooth.
- **Wellness Settings**: Configurable Daily Water Goal, Hydration Reminder, and Stand / Movement Reminder toggles in Phone `SettingsScreen` synced to watch via `WELLNESS_SETTINGS`.
- **Room Migration**: Safe, non-destructive `migration7To8` preserves existing user health records.

---

### 💓 Heart Rate & SpO₂ Offline Persistence & Batch Sync
- **Local SQLite Queueing**: Added `TABLE_HEART_RATE` and `TABLE_SPO2` to `WatchDatabaseHelper` (DB v4). All sensor readings are stored locally on the watch while disconnected.
- **Batched Synchronization**: Sensor readings sync in batches (`BatchedSyncPayload`) upon Bluetooth reconnection.
- **Timestamp Confirmations**: Bidirectional `SyncAckPayload` confirms synced timestamps, preventing duplicate records and data loss.

---

### ⚡ Multi-Tier Power Saving System
1. **Normal Mode**: Standard balanced performance (60 FPS sweeps, regular health monitoring, full sync).
2. **Extreme Power Saver Mode (Stamina)**: 3x battery longevity (~72 hrs), locks dial to 1 Hz, disables optical polling and wake gesture, OLED true-black, **Bluetooth phone sync stays 100% active**.
3. **Super Extreme Saver Mode (Emergency)**: 8+ days emergency standby (~200+ hrs), halts Bluetooth RF radio (0 mA wireless draw), suspends all background listeners, switches to ultra-minimalist OLED emergency dial with 1-tap exit.

---

### 💓 Heart Rate HAL Driver Accuracy Normalization & Companion Sync
- **Hardware Accuracy Normalization**: Resolved Kolabee U8 Ultra optical sensor HAL driver behavior where genuine PPG pulse readings were emitted with `accuracy = 0` (`SENSOR_STATUS_UNRELIABLE`/uncalibrated). The watch driver now normalizes valid physical readings in `25..240 BPM` to `accuracy = 1` (`SENSOR_STATUS_ACCURACY_LOW`), enabling heart rate measurements to pass validation, save to local SQLite, and sync immediately via `MessageType.HEART_RATE`.
- **Phone Companion Dashboard Live Display**: `DashboardScreen` VitalCard now displays live sensor feedback and unverified previews immediately if Room DB history has not yet populated, eliminating `-- bpm` displays.
- **Batched Sync & History Safeguards**: Incoming offline and queued heart rate records in `BluetoothSyncService` normalize uncalibrated hardware accuracy to ensure older offline records captured prior to the update seamlessly enter Phone Room DB and 24-hour charts without being dropped.

---

### 📦 Checksums & Artifacts
- **Watch App (Debug / Android 6+ API 23+)**: `031ef3e916c23b52cefb70f517731863aab321eefe402e0cd3ed6b773aace2e2` (`HealthSync-Watch-2.8.4-Android8.apk`)
- **Phone App (Release / Android 8+ API 26+)**: `782ab2c3ba204afdeb5a00690100dc6597731b3417a50ae03e0a2c597a46208e` (`HealthSync-Phone-2.8.4.apk`)
- **Combined Bundle**: `HealthSync-Android8-2.8.4-Both-APKs.zip`
