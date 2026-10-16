# HealthSync 2.9.4 — Bluetooth tethering button

The phone now has a visible **Bluetooth tethering** button under **Device → Internet for watch apps**. It is available without expanding setup and works as a settings shortcut even before connecting the watch.

One tap opens Android's tethering controls where the phone supports the shortcut. Turn on **Bluetooth tethering** there, then enable **Internet access** for the same paired phone on the watch. Phones without a direct tethering screen open network settings or main Settings with guidance instead.

The button opens Android controls; it does not automatically enable tethering or claim that internet sharing is active. Android-verified watch internet status continues to show the actual connection. Firmware must support Bluetooth tethering/PAN.

## Update

- **Phone:** Update to 2.9.4, code 32, using HealthSync-Phone.apk or in-app Check for updates. Android 8/API 26 or newer.
- **Watch:** 2.9.3, code 37, remains current and compatible. No watch update is needed if it is already on 2.9.3. Its unchanged APK is included for users on older versions.
- Install over the existing app to retain history. The phone APK uses the existing signing certificate. Android still asks for installation confirmation.

129 phone unit tests passed; the release build and lint check succeeded with no lint errors. Existing lint warnings remain. APK hashes and signing continuity were verified. Actual device settings navigation and Bluetooth traffic have not been tested on physical hardware.
