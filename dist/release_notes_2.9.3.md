# HealthSync 2.9.3 — Bluetooth internet setup and status

Set up internet for watch apps through your existing phone/watch Bluetooth pairing, where both devices support Android Bluetooth tethering.

- **Phone:** Open Device → Internet for watch apps for setup instructions, a network settings shortcut, and watch-reported internet status.
- **Watch:** Open Settings → Phone connection → Internet from phone for live connection status and Bluetooth/Wi-Fi settings shortcuts.
- Bluetooth internet is shown as verified only when Android confirms an internet-capable Bluetooth default network. Wi-Fi, mobile data, VPN, and unverified connections have separate states.
- Stale or disconnected reports cannot continue showing a current sharing success. Older app versions remain compatible with the extended telemetry.

Enable **Bluetooth tethering** in phone Android settings, then **Internet access** for that same paired phone in watch Bluetooth settings. Internet sharing uses Android's network profile alongside HealthSync sync. If the firmware lacks those options, use your phone's Wi-Fi hotspot instead. See the attached Bluetooth internet guide.

## Install or update

- Phone: **HealthSync-Phone.apk**, version 2.9.3, code 31; Android 8/API 26 or newer.
- Watch: **HealthSync-Watch.apk**, version 2.9.3, code 37; Android 6/API 23 or newer.
- Update both apps over the existing installations to retain history. APKs retain the previous release's signing certificate.
- Existing users can check for updates in phone Settings/Device. Android still asks for installation confirmation on each device.

## Validation

366 unit tests passed. Both APK builds and lint checks succeeded with no lint errors; existing lint warnings remain. APK signatures and hashes were verified. Bluetooth internet traffic and firmware support have not been tested on physical devices.
