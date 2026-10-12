# HealthSync 2.7.0 installation

Prepared 12 October 2026. Install **HealthSync-Watch-2.7.0-Android8.apk** (watch code 26) and **HealthSync-Phone-2.7.0.apk** (phone code 20). Watch requires Android 6/API 23 or newer; phone requires Android 8/API 26 or newer. Bluetooth Classic RFCOMM must be exposed by both devices.

1. Transfer each APK to its device. Open it in a file manager and allow that source to install apps when Android asks.
2. Install over the existing app with its matching certificate. Both packages retain this project's development signing certificate. Avoid uninstalling if you need saved history.
3. Update both devices. Earlier oxygen and sleep records cannot be proven real and are moved out of active history into a local unverified archive during this upgrade. Workouts, step history, and accepted heart-rate records remain.
   Phone database versions 4–5 retain verified active history. Undocumented database versions 1–3 instead preserve their original tables in local versioned archives and start empty active history. Destructive migration fallback is disabled; the app does not guess old records' meaning or source. Database versions differ from the displayed app release version.
4. Open both apps, pair in Android Bluetooth settings, then use phone **Device → Connect**. A manual disconnect stays paused until Connect is selected.
5. Grant sensors/activity/location for health and workouts as needed. Unsupported sensors show unavailable; they never generate fallback values. On Android 13+, continuous body-sensor capture may need separate all-time access in App permissions. Firmware/installer can restrict that access.
6. For notification/media features, enable phone Notification Access. Grant watch Notification Access separately for watch-source inbox management. App notification permission and DND access are separate grants.
7. Configure calls using [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md). Android 12+ phones require companion association for full call controls. Talking through the watch requires firmware Bluetooth call-audio support.
8. Set profile and step goal in phone Settings, then Apply to Watch while connected. Calories and stride distance are estimates.

## Watch navigation and Home recovery

From the clock: up → Apps, down → Controls, left → Notifications, right → Fitness, hold → face picker. The grid has direct health/tool routes. Health pages offer Back and Clock. Back returns through launcher panels; Home returns to the clock.

Open **Settings → Home app → Use HealthSync as Home** only after checking Apps and Android Settings. The same page offers **Choose / revert default Home** and **Open another Home app**. Keep the original launcher installed. Home selection is optional; Android controls process lifecycle.

## In-app updates

Use phone Settings/Device for updates or watch Settings for Wi-Fi updates. The new updater verifies SHA-256, package/version, and installed signing certificate. Phone-to-watch delivery distinguishes transmission, watch verification, and Android installation. Confirm installation on the receiving device. Earlier watches cannot report verification to the phone; inspect their update notification.

If Android asks to allow HealthSync as an installation source, enable it, return, and choose Install again or tap the watch's verified update notification. Unknown-source permission does not mean the update is installed.

## Verification

`SHA256SUMS-Android8.txt` covers the versioned APKs; `SHA256SUMS.txt` covers the stable aliases. `HealthSync-Android8-2.7.0-Both-APKs.zip` bundles both apps, manifest, hashes, guides, report, and diagnostic helpers.

Read `BUILD-REPORT-Android8-2.7.0.md` and the repository's `docs/HEALTH-DATA-CONTRACT.md` for software evidence and required hardware checks. No physical phone/watch was connected during this update. Real sensor accuracy, circular layout, call audio, OEM permissions, and battery use need device testing.

For USB installation diagnostics, explicitly choose the current APK:

```powershell
.\tools\Install-Watch-Diagnostic.ps1 -Serial 'WATCH_SERIAL' -ApkPath '.\dist\HealthSync-Watch-2.7.0-Android8.apk' -CheckStartup
```

From an extracted bundle, use `Install-Watch-Diagnostic.ps1` with the local versioned APK. See the included diagnostic guides. The helper preserves app data and does not select Home.
