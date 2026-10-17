# HealthSync 2.11.0 navigation build report

Prepared 17 October 2026. Phone **2.11.0 / code 34**; watch **2.11.0 / code 39**. Release packages, stable APK aliases and updater metadata use the exact verified APKs listed below. Release downloads are published before activating the main update feed.

## Behavior

A dedicated watch Navigation app shows structured Google Maps notification instructions, next-turn distance, the actual supplied icon or a conservative explicit-direction fallback, and available road/trip text. The phone has an explicit navigation opt-in and notification-access setup. No phone screen capture is required for directions. The previous screen viewer remains separately labeled as optional.

Route notifications, removal, access loss and reconnects feed a bounded navigation protocol over existing Bluetooth. The watch clears guidance on disconnect or 20-second expiry. The sender coalesces superseded snapshots and drops navigation queued over five seconds. Unknown instructions retain source text without guessed arrows. Heartbeats, distance/ETA updates, unknown maneuvers and reconnect snapshots do not repeatedly vibrate. Ordinary Maps notifications remain available for older watches.

## Executed verification

| Check | Observed result |
| --- | --- |
| Baseline unit suites | 402 tests; 0 failures/errors |
| Final phone unit tests | 172 passed; 0 failures/errors/skips |
| Final watch unit tests | 277 passed; 0 failures/errors/skips |
| Total | 449 passed; 47 added tests |
| Phone lintDebug | 0 errors; 8 existing warnings; 0 new warnings |
| Watch lintDebug | 0 errors; 426 existing warnings; 0 new warnings |
| Phone optimized release APK | BUILD SUCCESSFUL |
| Watch compatible debug APK | BUILD SUCCESSFUL |
| Protocol parity | Phone/watch NavigationModels match after package declarations |
| Source freshness | Navigation test reports and APKs postdate relevant source changes |
| Whitespace | git diff --check exits 0 |
| APK metadata | Expected package IDs, 2.11.0 versions, codes 34/39; minimum APIs 26/23 |
| Signatures | Both verify with the existing development certificate; watch v1 and v2 verified |

The combined six-task validator first passed in 2m 57s. Two new fixed-orientation lint warnings were then resolved by allowing NavigationActivity to follow normal orientation/configuration changes; the final combined validator passed in 46s. A separate artifact validator passed, comparing warning identities against saved pre-change reports and verifying fresh test/build evidence.

Before publication, extra empty lines at the end of four new Kotlin files were removed without changing source tokens. A full combined build/lint/test recheck passed in 38s; both APK hashes remained identical. Both unit-test tasks were then explicitly executed again in 6s, with all 449 tests passing. Release validation accepts Gradle's verified unchanged build outputs and checks their exact hashes rather than requiring cached APK file timestamps to change.

```powershell
python .agents/skills/gravitas/scripts/verify.py --cmd 'gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug --console=plain'
python .agents/skills/gravitas/scripts/verify.py --cmd 'python .gravitas/sessions/watch-navigation/validate-artifacts.py'
```

Logs and metrics are in `.gravitas/sessions/watch-navigation/`. Added executable checks cover conservative Maps parsing, localized source text, next-turn versus trip distances, legacy capability behavior, payload bounds/nulls, outbound coalescing/expiry, receiver sequence/reconnect/stale/inactive transitions, haptic noise, bounded PNG headers and circular geometry at 240/320/400px and rectangular configurations.

Phone APK SHA-256: `108493d69f40de09b4e4f36d0198e19e22bf2766700cbe894e05f5612b46650c`.

Watch APK SHA-256: `4309f7ab0953e0f932b48e94667bdcab572f704c8d3c2b1f8516ebd9ba371b15`.

Signer SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

## Physical verification limits

ADB reported no devices and the emulator had no configured AVDs. No phone/watch navigation session, Android permission flow, installation, screenshot/rendering, Maps-specific notification format, source icon, haptic, rotary interaction, locked-phone delivery or Bluetooth timing was exercised. The five-second source check uses Android handler scheduling; a fixed cadence while sleeping/dozing is not established by unit tests. OEM restrictions can delay the listener or background transport, in which case the watch hides expired guidance.

Maps versions/languages can expose different notification fields; custom-only notifications may omit usable instructions. The feature does not calculate its own route, position, map, lane guidance or ETA. Use the device acceptance checklist and setup in [WATCH-NAVIGATION.md](WATCH-NAVIGATION.md) before relying on the navigation view.
