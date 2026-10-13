# HealthSync v2.8.3 — Watch Hardware Telemetry, Two-Way Hydration & Sensor Sync

HealthSync 2.8.3 delivers comprehensive quality assurance hardening across both the Watch App and Phone Companion, featuring real-time watch battery & hardware diagnostics, two-way hydration sync, offline Heart Rate & SpO₂ local SQLite queueing, and hardened power saving modes.

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

### 📦 Checksums & Artifacts
- **Watch App (Debug / Android 6+ API 23+)**: `fde4e344c6249c6a677ff2522ee7048710b9ee2868c9c1fff464a82587379ce5` (`HealthSync-Watch-2.8.3-Android8.apk`)
- **Phone App (Release / Android 8+ API 26+)**: `ad6fe06ffab2e03b2f9c118739e926b63ce97d4460401163cc446b8d5fa8fa1c` (`HealthSync-Phone-2.8.3.apk`)
- **Combined Bundle**: `HealthSync-Android8-2.8.3-Both-APKs.zip`
