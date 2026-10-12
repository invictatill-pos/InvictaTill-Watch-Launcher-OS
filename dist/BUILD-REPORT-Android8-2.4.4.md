# HealthSync Update Delivery — Watch 2.4.4 / Phone 2.3.4

Prepared 12 October 2026. Watch version code 20, minimum Android 6.0/API23. Phone version code 14, minimum Android 8.0/API26.

## Result

This update launches **Phase 5 (Watch Face Creator Studio & Photo Dial Maker)** and enhances the **Supercharged Dual OTA Engine**:
- **Phase 5: Watch Face Creator Studio (Phone App)**:
  - Interactive Watch Face Maker Studio with live 1:1 smartwatch canvas viewport and ambient AOD preview.
  - Personal Photo Dial creator with phone gallery picker and smartwatch resolution/aspect optimizer (320×320).
  - Customizable dial ticks (12h, 60m), numeral markers (Roman, Arabic, Cardinal, Dots), and hand silhouettes (Sword, Baton, Needle, Arrow).
  - Smart complications layout (Live Heart Rate gauge, Step progress ring, Watch Battery meter, Date aperture).
  - My Creations collection with 1-tap Bluetooth beam to Kolabee U8 Ultra.
  - Direct "Customize in Creator Studio" action from curated store watch face sheets.
- **Dynamic Watch Face Engine (.hswf)**:
  - Base64 bitmap background decoding on both Watch hardware Canvas and Phone Compose previewer.
  - Real-time 60 FPS hardware-accelerated rendering and automatic on-wrist activation.
- **Supercharged Dual OTA Engine**:
  - Suspending backpressure queues (`Channel.send()`) preventing RFCOMM drops.
  - 8 KB chunk size with offset-indexed `RandomAccessFile` assembly on watch.
  - Live duplex progress reporting (`OTA_PROGRESS`) and `DEVICE_INFO` telemetry.
