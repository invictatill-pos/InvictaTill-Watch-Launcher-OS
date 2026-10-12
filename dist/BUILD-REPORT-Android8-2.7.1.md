# HealthSync 2.7.1 build report

Prepared 12 October 2026 for the Kolabee U8 Ultra sensor/workout fixes.

| Package | Variant | Version | Build code | Minimum Android |
| --- | --- | --- | --- | --- |
| `com.healthsync.watch` | Debug, v1/v2 signed | 2.7.1 | 27 | 6 / API 23 |
| `com.healthsync.phone` | Optimized release, v2 signed | 2.7.1 | 21 | 8 / API 26 |

Both packages use the existing Android development certificate. Signature verification passed. Certificate SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

## Changes

- HR/oxygen capture retries compatible sensors sequentially when the preferred sensor registers but does not deliver an accepted sample.
- Plausible accuracy-zero HR and saturation values are displayed as unverified sensor feedback, separately from accepted records.
- Failed/missing/denied/timed-out capture checks exported health-app records. Imports require an explicit metric, valid original epoch capture time, range, and app identity. Source and original time survive watch cache, Bluetooth sync, and phone history. Repeated exports are deduplicated.
- Health screens offer original-app opening and an immediate shared-data check. Android 14 app-only imports use a bounded short service when body/activity permission prevents the health service from starting; reconnect can replay saved imports without starting sensors.
- Workout tracking uses GPS/network/OEM and passive locations shared by other watch apps. Recent caches must belong to the workout/resume interval. Retry restarts subscriptions; stalled subscriptions recover after two minutes. Mock/stale/reordered/weak fixes cannot generate route distance.
- Phone Room migration 6→7 adds provenance without deleting history or archives. Existing migration paths remain supported.

## Validation completed

| Check | Result |
| --- | --- |
| Watch debug unit tests | 220 tests, zero failures/errors |
| Phone debug unit tests | 100 tests, zero failures/errors |
| Watch/phone debug Android lint | Passed, zero errors; 373 watch warnings/2 informational items, 8 phone warnings |
| Watch debug APK | Built successfully after final permission/import changes |
| Phone optimized release APK | Built successfully |
| SQLite migration and generated DAO queries | Passed versions 1–6→7, original archive/history preservation, rollback, imported timestamp/provenance, deduplication, equal-time sensor preference |
| APK signatures | Verified; watch v1/v2, phone v2 |
| Git whitespace check | Passed |

Commands:

```powershell
.\gradlew.bat :WatchApp:app:testDebugUnitTest :PhoneApp:app:testDebugUnitTest :WatchApp:app:lintDebug :PhoneApp:app:lintDebug :WatchApp:app:assembleDebug :PhoneApp:app:assembleRelease '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
.\gradlew.bat :WatchApp:app:testDebugUnitTest :WatchApp:app:lintDebug :WatchApp:app:assembleDebug '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
python tools/Test-Health-Migrations.py
.\tools\update_dist.ps1 -WatchVersion 2.7.1 -PhoneVersion 2.7.1
```

The watch was rebuilt after the final bounded-service/reconnect changes; the second command describes that final successful verification. Build/migration evidence is saved locally under `validation/`. The distribution includes exact artifact SHA-256 hashes and the fallback guide.

## Physical testing still required

ADB reported no connected device. No claim is made that these software checks establish the actual U8 Ultra's sensor accuracy, original apps' export schemas, GPS hardware/reception, OEM permissions, or battery use. The user's supplied app display names identify candidate apps, not their package/provider contract.

If **Heart rate** or **Blood oxygen** keeps its readings private, Android cannot provide them to HealthSync. The fallback reports this and opens the original app. A vendor-specific adapter needs the actual documented export or SDK; missing measurements are never fabricated. Use [WATCH-HEALTH-FALLBACK.md](WATCH-HEALTH-FALLBACK.md) for physical acceptance steps.

This report covers local build validation. Publishing the release does not establish physical sensor/export compatibility. No device installation was performed during validation.
