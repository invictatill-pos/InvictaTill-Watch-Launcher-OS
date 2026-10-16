# HealthSync 2.10.0 — Live Google Maps on your watch

See the actual Google Maps screen and visible navigation from your phone on the watch through the existing Bluetooth connection.

- On the phone, open **Device → Google Maps on watch → Share Google Maps** and approve Android screen sharing. On Android versions that offer single-app sharing, choose **Google Maps**.
- On the watch, open **Apps → Live phone map**. The full-screen view has round-safe controls, Fit, pinch zoom, drag to pan, double-tap zoom and a scrollable Help sheet. Controls hide while images are live; tap once to show them.
- Stop from either app, the sharing notification or Android's capture controls. Sharing stops on phone lock/screen-off, Bluetooth loss or revoked capture. Reconnecting requires fresh approval.
- Images refresh about every 1.5 seconds or slower. Keep the phone unlocked with Maps visible. Navigation and route controls remain on the phone. The watch does not need its own internet connection to receive images.

Update **both apps to 2.10.0**: phone code **33**, watch code **38**. Install over the existing apps to retain history; both retain their existing signing certificate. Android still asks for installation confirmation. Phone requires Android 8/API26 or newer; watch requires Android 6/API23 or newer.

Whole-screen capture can show other apps and notifications; prefer single-app sharing where Android offers it. Screen frames stay in bounded device memory and are not recorded or uploaded by HealthSync.

402 unit tests passed; both APK builds, lint checks with zero errors, signatures, versions and hashes were verified. Existing lint warnings remain. Actual capture quality, map readability, Bluetooth throughput and device firmware behavior still require phone/watch testing.

See **LIVE-PHONE-MAP.md** and **ANDROID8-INSTALL.md** in the downloads for setup.
