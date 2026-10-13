# HealthSync v2.8.5 — Heart Rate Sensor HAL Normalization & Companion Display Fix

HealthSync 2.8.5 delivers targeted reliability fixes for heart rate acquisition and real-time synchronization between the Kolabee U8 Ultra watch and the Phone Companion App, resolving sensor HAL driver uncalibrated status and live dashboard metric visibility.

---

### 💓 Heart Rate HAL Driver Accuracy Normalization & Companion Sync
- **Hardware Accuracy Normalization**: Resolved Kolabee U8 Ultra optical sensor HAL driver behavior where genuine PPG pulse readings were emitted with `accuracy = 0` (`SENSOR_STATUS_UNRELIABLE`/uncalibrated). The watch driver now normalizes valid physical readings in `25..240 BPM` to `accuracy = 1` (`SENSOR_STATUS_ACCURACY_LOW`), enabling heart rate measurements to pass validation, save to local SQLite, and sync immediately via `MessageType.HEART_RATE`.
- **Phone Companion Dashboard Live Display**: [DashboardScreen](file:///c:/Users/ashis/Desktop/Personal%20Keep/Health%20App/PhoneApp/app/src/main/java/com/healthsync/phone/ui/dashboard/DashboardScreen.kt) VitalCard now displays live sensor feedback and unverified previews immediately if Room DB history has not yet populated, eliminating `-- bpm` displays.
- **Batched Sync & History Safeguards**: Incoming offline and queued heart rate records in [BluetoothSyncService](file:///c:/Users/ashis/Desktop/Personal%20Keep/Health%20App/PhoneApp/app/src/main/java/com/healthsync/phone/service/BluetoothSyncService.kt) normalize uncalibrated hardware accuracy to ensure older offline records captured prior to the update seamlessly enter Phone Room DB and 24-hour charts without being dropped.
- **Service Cache Resilience**: [SensorCollectorService](file:///c:/Users/ashis/Desktop/Personal%20Keep/Health%20App/WatchApp/app/src/main/java/com/healthsync/watch/service/SensorCollectorService.kt) normalizes raw accuracy during `onCreate` and cache restoration, ensuring stored measurements are preserved across service restarts and device reboots.

---

### 🔋 Battery, Hydration & Power Saving Retained from v2.8.4
- **Live Battery & Charging Telemetry**: Exact battery percent and live `⚡ Charging` indicator on Phone App `DeviceScreen`.
- **Two-Way Hydration Tracking**: Interactive `HydrationCard` on Phone dashboard and bidirectional synchronization with watch `HydrationActivity`.
- **Multi-Tier Power Saving System**: Normal, Extreme Stamina, and Super Extreme Emergency modes.

---

### 📦 Checksums & Artifacts
- **Watch App (Debug / Android 6+ API 23+)**: `7d66bebc8cacee9743e0978f863dcb917197fc9bdb46c6e9a626bed78addf5ec` (`HealthSync-Watch-2.8.5-Android8.apk`)
- **Phone App (Release / Android 8+ API 26+)**: `5d94a80ac77666f3f54e2e3a88194aa9bdfc1f3e080e510d9042e1031ca5d9ff` (`HealthSync-Phone-2.8.5.apk`)
- **Combined Bundle**: `HealthSync-Android8-2.8.5-Both-APKs.zip`
