# HealthSync communication delivery — Watch 2.4.0 / Phone 2.3.0

Prepared 9 October 2026. Watch version code 16, minimum Android 6.0/API23. Phone version code 10, minimum Android 8.0/API26. Both retain the existing application IDs and development signing configuration for in-place updates.

## Result

This update strengthens incoming calls, caller identity, call controls, message presentation, replies and reconnect behavior. Existing fitness and sensor behavior is retained. Install both APKs together and follow [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md).

- Phone caller names use Telecom identity, permitted Contacts lookup and trusted default-dialer notification metadata. Modern full call controls use an explicitly associated companion watch and Android InCallService. The existing phone dialer remains in place.
- Call controls use the current call identity and request IDs. Answer/decline/end, mute and audio route labels wait for phone confirmation; errors and timeouts do not claim success. Legacy Android 8–11 answering is preserved; decline/end requires API28+. Multiple legacy calls disable ambiguous controls.
- The active call timer uses the phone's original connected timestamp. Answering from the watch requests its supported call audio connection when available. The exact connected watch must be among Telecom's supported Bluetooth call devices. Another headset is identified separately; global SCO/media-key workarounds are removed. Legacy audio/mute controls remain unavailable.
- Calls have a separate high-priority channel and Android full-screen permission/eligibility. The call screen requests screen-on and locked-screen display through supported Android APIs. Visible incoming calls have bounded reminder vibration respecting DND and channel vibration preferences. Messages do not replace call screens.
- New messages request a bounded screen wake and popup on compatible unlocked Android 8 watches, including with launcher management enabled. Newer background delivery uses private high-priority banners where Android restricts launching. Popup/wake/channel controls are visible in Watch notifications. Ordinary messages do not use full-screen call permission.
- Message details include sender, conversation name, expanded text, app and time. Blank/group-summary noise, repeated content, silent channels and background progress are handled quietly. Phone VoIP call notifications can appear as ordinary alerts when exposed by their app; dedicated cellular call controls apply to Telecom calls.
- Reconnect snapshots silently restore current notifications and call state, retire ended/filtered notification actions, and remove stale system copies. Disconnection/revoked notification access disables stale replies while saved text remains readable.
- Reply results distinguish sending, acceptance by the source app, explicit failure and unconfirmed timeout. Requests/results are correlated with notification key and request ID; duplicate request IDs cannot send a second reply. App acceptance is not a network/recipient delivery receipt.
- Watch inbox schema3 additively migrates schema1/2, preserving source/identity/text/read state while adding current notification and reply metadata. Kotlin/Gson default handling is normalized for older phone notification/call payloads.

## Verification

The final Gradle run uses:

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug
```

All **236 JVM tests passed**: Watch161, Phone75, zero failures/errors/skips. Coverage includes call capabilities/identity/stale events, audio route confirmation, legacy payloads, auto-routing after watch answer, content extraction, quiet replay, notification identity, reply safety, popup policy, and existing fitness/sensor/navigation rules.

Android debug lint: **zero errors**, Watch334 warnings and Phone6 warnings. Warning details are retained in the XML reports. They include the existing hardcoded text/localization, drawable/manifest, SDK/dependency and deprecation recommendations; this delivery does not claim a warning-free build. The specifically documented companion MANAGE_ONGOING_CALLS permission uses one narrow ProtectedPermissions suppression because Android grants that access through physical watch association. Runtime association and capability checks remain in place. No NewApi or MissingPermission errors are suppressed for the new call/notification paths.

APK metadata, signatures, certificate continuity, ZIP alignment and bundled hashes are recorded beside the final test/lint evidence under `validation/communication-2.4.0/`. APK checksum references are in [SHA256SUMS-Android8.txt](SHA256SUMS-Android8.txt).

Earlier intermediate checks caught a missing default for legacy Gson strings, a concurrently compiled key-length assertion, API-level guards and direct permission checks. These were corrected before the final delivery. Earlier failed logs remain for traceability; use `delivery-build.log`, `unit-tests.json` and `lint-summary.json` for final results.

## Device limits and remaining checks

No physical watch, phone, or emulator was connected during this delivery. Automated tests and package checks do not establish physical behavior.

Talking through a watch requires microphone/speaker hardware and firmware Bluetooth Calls/HFP support. A separate call Bluetooth address is not automatically paired with HealthSync's data address. HealthSync cannot turn a Bluetooth fitness data connection into cellular call audio. Unknown/private caller information cannot be invented.

Modern companion access needs Android12+, Android companion setup support and explicit user association. Legacy call APIs remain subject to OEM restrictions. Screen wake, full-screen eligibility, DND, secure locks, notification grants, force-stop and vendor background/power behavior remain Android/firmware controlled.

Follow the physical checklist in [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md): incoming/answered/missed/ended calls, caller names/private calls, multiple calls, actual two-way speech, mute/routes/other headsets, screen-off messages and calls, DND/lock/channel controls, replies/removals/filters, disconnect/restart/reconnect and battery use.

Source baseline is retained at `validation/communication-2.4.0/source-backup/`. Previous APKs and reports remain available for their respective versions.
