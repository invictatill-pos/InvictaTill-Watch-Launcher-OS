# HealthSync 2.9.4 build report

Prepared 16 October 2026. Phone **2.9.4 / code 32**; compatible watch remains **2.9.3 / code 37**.

## Changes

- A prominent Bluetooth tethering button is visible under phone Device → Internet for watch apps without expanding setup.
- One tap tries Android's tethering controls, then network settings and main Settings if the manufacturer does not expose the direct screen.
- Help and fallback messages explain that the user must turn on the Android switch. Opening settings never reports tethering enabled.
- The button is available when the watch is disconnected. Existing verified internet status remains the authority for connection success.
- No watch source or APK change is included. The 2.9.3 watch package is reused byte for byte.

## Verification

| Check | Result |
| --- | --- |
| Phone unit tests | 129 passed; 0 failures/errors/skips |
| Phone lintDebug | 0 errors; 8 existing warnings |
| Phone optimized release APK | BUILD SUCCESSFUL |
| Phone signature | Verifies; matches public 2.9.3 signing certificate |
| Watch compatibility artifact | Exact public 2.9.3 APK hash retained |
| Source whitespace | git diff --check passed |

The phone validation command completed successfully in 1 minute 24 seconds:

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :PhoneApp:app:assembleRelease --console=plain
.\tools\update_dist.ps1 -WatchVersion 2.9.3 -PhoneVersion 2.9.4 -ReleaseVersion 2.9.4
```

Phone APK SHA-256: `28f9570c4e33e0361b8375408e148d4f3cb0bf1a9f3aa3534016993fef2f9fc9`.

Unchanged watch APK SHA-256: `dba3592b522c711b98d7578f0bd0780e5e0b10cbc4665c9e0a7582990ba0be41`.

Signer SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

No phone/watch was attached for testing Android settings navigation or actual Bluetooth tethering traffic. Firmware and carrier support remain necessary. See [BLUETOOTH-INTERNET.md](BLUETOOTH-INTERNET.md). The unchanged watch's earlier test and lint evidence is recorded in the 2.9.3 build report.
