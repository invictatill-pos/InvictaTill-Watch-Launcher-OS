# HealthSync 2.10.0 build report

Prepared 16 October 2026. Phone **2.10.0 / code 33**; watch **2.10.0 / code 38**.

## Changes

Real user-approved Android screen capture shares the visible Google Maps phone view over the existing Bluetooth link. The watch uses a full-screen view with round-safe controls, circle-aware Fit, pinch zoom, pan, double-tap zoom and Help. Sharing requires fresh approval each session and stops on explicit Stop, revoked capture, phone lock/screen-off or Bluetooth loss. Frames remain in bounded memory with acknowledgment backpressure and session/peer checks.

## Verification

| Check | Result |
| --- | --- |
| Phone unit tests | 145 passed; 0 failures/errors/skips |
| Watch unit tests | 257 passed; 0 failures/errors/skips |
| Phone lintDebug | 0 errors; 8 warnings |
| Watch lintDebug | 0 errors; 426 warnings |
| Phone optimized release APK | BUILD SUCCESSFUL |
| Watch compatible debug APK | BUILD SUCCESSFUL |
| APK signatures | Both verify with the existing development certificate; watch retains v1/v2 signatures |
| Protocol models | Match across phone and watch |
| Source whitespace | git diff --check passed |
| Watch geometry tests | Circular overlay bounds at 240/320/400px; full-image Fit corners and aspect ratio |

The final combined six-task validation completed in 1 minute 23 seconds. After the final watch layout changes, the affected watch tests, lint and APK build passed again in 43 seconds. No phone source changed after its successful final validation.

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug --console=plain
.\gradlew.bat :WatchApp:app:testDebugUnitTest :WatchApp:app:lintDebug :WatchApp:app:assembleDebug --console=plain
.\tools\update_dist.ps1 -WatchVersion 2.10.0 -PhoneVersion 2.10.0 -ReleaseVersion 2.10.0
```

Phone APK SHA-256: `393f516a041619eaa2526b1cd7d1ccf5621a5e853c448cd97f8e203e9824eb74`.

Watch APK SHA-256: `7de94dc98b4fed72caf13c122b02bb6f03220c9335bc64cb53291462b59c6d2f`.

Signer SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

No physical phone/watch or emulator was attached. Actual capture quality, map labels, Bluetooth throughput, touch comfort, battery use, firmware behavior and Android consent/install flows need device testing. Older Android whole-screen capture may include other apps/notifications; prefer single-app capture where available. See [LIVE-PHONE-MAP.md](LIVE-PHONE-MAP.md) for setup and the device acceptance checklist.
