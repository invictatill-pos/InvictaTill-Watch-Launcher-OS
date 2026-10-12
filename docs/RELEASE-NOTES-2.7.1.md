# HealthSync 2.7.1 — Sensor fallback and workout GPS recovery

HealthSync 2.7.1 improves missing heart-rate/oxygen readings and stalled workout location tracking. It tries compatible watch sensors, then checks timestamped measurements shared by installed health apps when sensor capture fails.

- Retry compatible HR and oxygen sensors when the preferred sensor registers but does not supply an accepted sample.
- Show plausible accuracy-zero readings as **Unverified sensor** feedback, separate from health history and workout statistics.
- Import supported health-app exports with their original capture time and app identity; synchronize them to the phone without duplicate history.
- Open the watch's original **Heart rate** and **Blood oxygen** apps, with controls to check shared data immediately after measuring there.
- Recover stalled workout location subscriptions, support fresh locations shared by other watch apps, and report weak reception/permission/provider states.
- Preserve existing history and archives while adding source information to the phone database.

Update **both** apps: watch 2.7.1 (build 27), phone 2.7.1 (build 21). Install over the existing packages to retain data. Signing certificates remain compatible with previous project builds. Android still asks the device user to confirm installation.

Downloads:

- [Watch APK](https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/download/v2.7.1/HealthSync-Watch.apk)
- [Phone APK](https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/download/v2.7.1/HealthSync-Phone.apk)
- [Both APKs and guides](https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/download/v2.7.1/HealthSync-Android8-2.7.1-Both-APKs.zip)

Validation: 220 watch tests and 100 phone tests passed; Android lint reported zero errors. Migration/data-preservation checks and APK signature verification passed. SHA-256 files and the build report are included.

No physical watch was connected during validation. App imports require a readable export from the original app; Android cannot provide private records merely because the app is installed. Real U8 Ultra sensor/export compatibility and GPS reception still require device testing. See the included **WATCH-HEALTH-FALLBACK.md** guide.
