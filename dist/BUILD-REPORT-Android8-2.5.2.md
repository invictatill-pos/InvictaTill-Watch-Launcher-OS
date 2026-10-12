# HealthSync Delivery — Watch 2.5.2 / Phone 2.5.1

Prepared 12 October 2026. Watch version code 24 (v2.5.2), minimum Android 6.0/API 23. Phone version code 18 (v2.5.1), minimum Android 8.0/API 26.

## Highlights

This release delivers commercial smartwatch grade sensor tracking: super-accurate step counting and continuous optical PPG heart rate measuring across both the standalone watch and companion phone app.

### 1. Super-Accurate Gait Step Tracking (Watch)
- **Zero-Step Root Cause Fixed**: Solved the issue where stub/dummy `TYPE_STEP_COUNTER` hardware reporting 0 on MediaTek Android 8.1 watches permanently prevented software step detection from ever running.
- **Dynamic Gravity Isolation (`linearAcc = mag - gravity`)**:
  - Continuous low-pass dynamic gravity tracking (`gravity = 0.88 * gravity + 0.12 * mag`).
  - Signed linear acceleration captures real downward footstrike peaks without frequency-doubling artifacts.
- **4-Step Gait Debounce**:
  - 4-step cadence buffer with strict 220–1800 ms human gait interval window eliminates phantom steps from arm gestures, typing, and jitter.
- **3000ms Hardware Watchdog**:
  - Accelerometer tracking runs continuously in parallel. If the hardware step counter stops incrementing for >3000ms, software steps seamlessly take over.

### 2. Commercial Smartwatch Heart Rate Measuring (Watch & Phone)
- **Optical PPG Sensor Acceptance**:
  - Expanded policy to recognize PPG, HRS, PAH, Tianyihexin, Pulse, and Heartbeat sensor hardware.
  - Optical sensors on Android 8.1/MediaTek often emit `accuracy = 0` (uncalibrated optical raw stream). These are now treated as valid human BPM readings (25–240 BPM), rejecting only disconnected sensors (`accuracy = -1`).
- **Continuous Measurement Window**:
  - Just like Apple Watch, Garmin, and Galaxy Watch, the optical sensor remains active for a 30-second continuous measurement window (or continuously during active workouts) rather than disconnecting after 1 sample.
  - Broadcasts live BPM updates continuously as your pulse fluctuates.
- **Clock Skew Guard**:
  - MediaTek/Android 8 optical sensors can emit timestamps relative to boot or raw nanos with clock skew. Captures wall-clock fallback so live optical frames are never rejected.
- **Workout & Tile Integration**:
  - Real-time workout tracking service streams fresh optical BPM directly into active workout screens, distance metrics, and calories burned.
  - Watch faces (Orbit Pro, Chrono Ultra, Retro Illuminator, Cyberpunk HUD, Bauhaus) and quick tiles display live pulse updates.
- **Phone Companion & Database Sync**:
  - Phone `SyncValidation` and Room database queries (`HeartRateDao`) now store and query optical readings (`accuracy >= 0`), populating clinical heart rate zones, 24-hour charts, and daily recovery analytics.

## Artifacts & Checksums

| File | SHA-256 |
|------|---------|
| `HealthSync-Watch-2.5.2-Android8.apk` / `HealthSync-Watch.apk` | `5a5c8265f44c53449b9e5c0ace2e709c5b6c5f41892df1f0670277895af762bd` |
| `HealthSync-Phone-2.5.1.apk` / `HealthSync-Phone.apk` | `7ac9a5efb9800c87feabd0182ee78c10f8d07b2d98d1b8398761b43034011569` |

All 166 Watch unit tests and 87 Phone unit tests passed with 100% success rate.
