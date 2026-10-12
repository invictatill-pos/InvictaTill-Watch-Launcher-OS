# HealthSync 2.7.0 build and validation report

Prepared 12 October 2026. App source revision: `fe2a17f349ba011019e9bfc95ac14bff1aa682fb`.

This release refreshes the phone and watch appearance, navigation and permission feedback; removes fabricated health values; preserves original capture timestamps and explicit estimate labels; and verifies update/face-transfer results.

## Exact artifacts

| App | Package | Code | Minimum Android | Target API | APK bytes |
| --- | --- | --- | --- | --- | --- |
| Phone 2.7.0 | `com.healthsync.phone` | 20 | Android 8 / API 26 | 34 | 1681770 |
| Watch 2.7.0 | `com.healthsync.watch` | 26 | Android 6 / API 23 | 34 | 7655811 |

Phone is an optimized release APK with v2 signing. Watch retains the v1/v2-signed debug delivery format used by existing installations. Both passed `apksigner verify` and retain the same certificate as their 2.6.0 APKs:

`a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`

APK SHA-256:

- Phone: `3af68e9a11b6f171d98d3099dc476a28568695f967357254e57409e7ca784740`
- Watch: `978112717df5f60675479878f34852b8c6cf750672b29f5bc01e3cbdedff275d`

Package identity, version code/name, minimum/target API and signatures were read from the actual APKs. Metadata/checksums are generated from these bytes, rather than guessed from a release tag.

## Software validation

| Check | Result |
| --- | --- |
| Phone unit suite | 94 passed; 0 failures/errors/skips |
| Watch unit suite | 201 passed; 0 failures/errors/skips |
| Phone lint | 0 errors; 8 warnings; 2 informational findings |
| Watch lint | 0 errors; 375 warnings |
| Optimized phone assembly | Passed |
| Watch assembly | Passed |
| Actual migration SQL checks | 12 scenarios passed on SQLite 3.49.1 |
| APK signing and installed-certificate continuity | Passed against both previous 2.6.0 APKs |
| Git whitespace/diff checks and changed XML parsing | Passed |

The 295 unit tests include capture validation, timestamp/range/accuracy rules, counter/detector exclusivity, real-zero/missing-step display, motion-rest coverage, body-profile assumptions, step-reset timestamp merging, workout/location provenance and route gaps, hydration/breathing/compass policy, transfer bounds/correlation and face acknowledgements, alongside existing feature regressions.

Known database4→6 and5→6 paths match the generated Room schema and retain workouts, steps, notifications and accepted heart-rate records while archiving unverifiable oxygen/rest rows. Unknown database1/2/3→6 fixtures cover empty/sparse/full old tables, raw blob/null preservation, columns/keys/indices/autoincrement/triggers, unrelated tables, Room metadata, and archive-collision transaction rollback. Unknown old health meanings are not recovered into active history. These checks execute production SQL with SQLite; Android/Room device migration testing remains part of hardware acceptance.

The remaining lint warnings are recorded in [the validation summary](https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/blob/v2.7.0/validation/2.7.0/summary.json). They mainly concern hardcoded/localized text, platform/deprecated API use, the retained target SDK, layout/optimization and static-reference checks. They were not hidden by a lint baseline. Compass permission errors, small XML labels and the audited face click-accessibility warnings were repaired.

Build environment: Gradle8.7, AGP8.5.0, Kotlin2.0.0, Android SDK/build-tools34, Android Studio OpenJDK21.0.10.

## Reproduce

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug
.\gradlew.bat :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug
python tools\Test-Health-Migrations.py
python tools\Collect-Validation-Summary.py
.\tools\update_dist.ps1 -WatchVersion 2.7.0 -PhoneVersion 2.7.0
```

The evidence collector reads completed `build-phone-final-2.7.0.log`, `build-watch-final-2.7.0.log`, and `build-artifacts-2.7.0.log`; retain those logs when reproducing its exact collection step. Unit/lint result XML is generated under each app's build directory. No user's database is opened by the migration tests.

## Device acceptance remains

ADB reported 0 attached Android devices. No physical phone/watch or running emulator was available. Runtime screenshots, touch fit on the actual circular screen, real sensor accuracy/off-wrist behavior, overnight motion coverage/battery, GPS exercise routes, OEM background permissions, call audio and on-device installation consent were not physically verified.

Use [HEALTH-DATA-CONTRACT.md](HEALTH-DATA-CONTRACT.md) and [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md) for device checks. Missing measurements remain unavailable; stillness, motion-step estimates, stride distance and calories must not be represented as directly measured physiology.

Install both APKs as updates over the existing packages. Legacy phone oxygen/rest rows move into local unverified archives. Unknown pre-v4 databases preserve their original tables in archives and start empty active history. The watch archives unverifiable live steps and hydration totals; its current live step count restarts and awaits a genuine observation. Historical step/workout tables remain preserved.
