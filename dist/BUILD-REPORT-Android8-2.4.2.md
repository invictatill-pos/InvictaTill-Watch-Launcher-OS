# HealthSync Dynamic Watch Face Delivery — Watch 2.4.2 / Phone 2.3.2

Prepared 10 October 2026. Watch version code 18, minimum Android 6.0/API23. Phone version code 12, minimum Android 8.0/API26.

## Result

This update completes **Phase 3 (Dynamic Watch Face Engine)**:
- **Declarative `.hswf` Schema**: Complete JSON specification for background layers, tick marks, number styles, skeleton/sword/baton hands, digital clock, date, complications, and low-power AOD mode.
- **`DynamicWatchFaceView` (Watch)**: 60 FPS hardware-accelerated Canvas engine rendering `.hswf` packages natively with live heart rate, steps, and battery complication bindings.
- **`DynamicFaceStore` (Watch)**: Manages installed dials in `filesDir/installed_watchfaces/` with starter dials (`Neon Cyberpunk` and `Bauhaus Minimal`).
- **`DynamicFacePreview` (Phone)**: 1:1 Jetpack Compose Canvas previewer for phone screens.
- **Bluetooth Installation Protocol**: Seamless companion transmission of `.hswf` watchfaces over RFCOMM.
