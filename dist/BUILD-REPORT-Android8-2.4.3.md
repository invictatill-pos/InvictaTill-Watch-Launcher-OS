# HealthSync Update Delivery — Watch 2.4.3 / Phone 2.3.3

Prepared 10 October 2026. Watch version code 19, minimum Android 6.0/API23. Phone version code 13, minimum Android 8.0/API26.

## Result

This update completes **Phase 4 (Online Watch Face Studio & Store)** and provides a **Supercharged Dual OTA Update Engine**:
- **Supercharged Dual OTA Updating**:
  - Fixed Bluetooth companion RFCOMM channel saturation by upgrading to suspending backpressure queues (`Channel.send()`), preventing packet drops and false disconnection aborts.
  - Tuned chunk size to 8 KB with offset-indexed `RandomAccessFile` on the watch, enabling idempotent and out-of-order resilient packet assembly.
  - Watch live duplex progress reporting (`OTA_PROGRESS`) and `DEVICE_INFO` telemetry reporting watch version, build, and battery level on connect.
  - Autonomous Wi-Fi update fallback on Watch with multiple CDN/GitHub raw mirrors.
- **Phase 4: Watch Face Studio & Store (Phone App)**:
  - Curated collection of luxury, sports, minimal, and cyberpunk dynamic dials (`Chrono Prestige`, `Neon Cyberpunk`, `Bauhaus Minimal`, `Vanguard Diver 300M`, `Aero Flight`, `Quantum Pulse`, `Solaris Executive`, `Nordic Slate`).
  - Interactive live preview sheet with AOD ambient toggle and real-time ticking simulation.
  - 1-Tap Bluetooth beam to Kolabee U8 Ultra with automatic installation and face activation.
