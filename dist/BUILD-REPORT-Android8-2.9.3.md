# HealthSync 2.9.3 build report

Prepared 16 October 2026. Phone version code **31**; watch version code **37**.

## Changes

- Phone Device screen provides Bluetooth internet setup instructions, Android network settings access, and phone/default watch internet status.
- Watch Settings → Phone connection → Internet from phone provides live default-network status and Bluetooth/Wi-Fi settings access.
- Sharing is confirmed only for an internet-capable, Android-validated Bluetooth default network. Wi-Fi, mobile data, VPN, missing, and unverified routes remain distinct.
- Watch telemetry includes optional network status without changing existing message types. Reports refresh on route changes and at least once per minute; the phone expires reports after 90 seconds and clears them when HealthSync disconnects.
- Existing Bluetooth pairing can support sync and system internet sharing when the firmware supports both profiles. Android system settings enable tethering; the app does not add a PAN driver or route other apps' traffic itself.

## Verification

| Check | Result |
| --- | --- |
| Phone unit tests | 129 passed; 0 failures/errors/skips |
| Watch unit tests | 237 passed; 0 failures/errors/skips |
| Added tests | 11 for status classification, freshness, and optional telemetry compatibility |
| Phone lintDebug | 0 errors; 8 existing warnings |
| Watch lintDebug | 0 errors; 424 existing warnings |
| Phone optimized release / watch debug APK | Both built successfully |
| APK signatures | Phone v2; watch v1/v2; both verify |
| Upgrade signing identity | Both match the existing 2.9.2 APK certificate |
| Release Gson metadata | Network payload names retained by R8 keep rule |
| Source whitespace | git diff --check passed |

Signer SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

Phone APK SHA-256: `d67432efc58414c7d22095a46ac39267fd515587905c184d1701f7e9fb1aae6a`.

Watch APK SHA-256: `dba3592b522c711b98d7578f0bd0780e5e0b10cbc4665c9e0a7582990ba0be41`.

The final coordinated validation completed with BUILD SUCCESSFUL:

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug --console=plain
.\tools\update_dist.ps1 -WatchVersion 2.9.3 -PhoneVersion 2.9.3
```

No physical phone/watch or emulator was connected. Real internet traffic, Kolabee U8 Ultra firmware PAN support, and simultaneous Bluetooth sync require device acceptance checks in [BLUETOOTH-INTERNET.md](BLUETOOTH-INTERNET.md). Automated tests do not establish those hardware capabilities.
