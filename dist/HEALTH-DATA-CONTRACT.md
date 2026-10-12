# Health data contract — 2.7.1

HealthSync displays captured records, clearly marked estimates, and explicitly manual entries. It never substitutes a plausible reading when hardware is unavailable or acquisition fails.

## Capture and display rules

- Direct heart-rate history requires BPM 25–240, accuracy 1–3, and a fresh, ordered original sensor timestamp from the active acquisition. Android heart-rate sensors and explicitly identified vendor BPM metrics qualify. Raw optical values, PPG waveforms, and heartbeat events do not. Plausible accuracy-zero BPM is shown as **Unverified sensor** feedback and never enters workout statistics/history.
- Direct oxygen capture requires an explicit vendor saturation metric, reliable accuracy, 50–100%, and a valid original sensor timestamp. Plausible accuracy-zero saturation is shown as **Unverified sensor** feedback separately from accepted captures. The first accepted capture ends acquisition. Failed acquisition ends within the measurement timeout.
- Acquisitions prefer the firmware default and try further compatible metric sensors if registration fails or the selected sensor does not deliver an accepted reading. Sensors run sequentially inside the existing 60-second HR / 30-second oxygen budget. Missing/denied/failed/timed-out sensors fall back to a bounded read-only check of installed health apps.
- App imports require an exported readable provider, explicit BPM or saturation columns (or a declared metric/unit schema), and an original epoch capture timestamp no more than 24 hours old. The source is `WATCH_HEALTH_APP`, with original package/label/time. HR accuracy zero means the app did not supply Android sensor accuracy. App readings do not claim independent verification of the providing app's hardware accuracy. Newer direct captures take precedence over older app exports. Duplicate exports do not create duplicate health records. Workout HR requires a fresh capture within the workout/resume interval, not a saved pre-workout value.
- Repeated cached frames, invalid timestamps, NaN/infinite values, unreliable accuracy, and invalid ranges cannot create a new health record. Replacing an invalid capture timestamp with callback delivery time is forbidden.
- Only one hardware step source counts at a time. Accelerometer estimates remain estimates. Daily totals and closed buckets synchronize without duplicate aggregation. App startup and midnight cannot establish a captured zero; steps remain unavailable until a validated sensor observation establishes a baseline or records movement. Counts describe recorded steps during monitoring, rather than promising complete day coverage.
- Motion rest requires continuously observed minute epochs and resets on sensor gaps/clock discontinuity. Rest onset/finalization requires real observation coverage. It carries source `WATCH_ACTIGRAPHY`, an estimated duration, zero stage fields, and `stagesAvailable=false`. Stillness cannot confirm sleep or identify deep/light/REM stages.
- GPS/network distance is labeled by source. Tracking also subscribes to passive Android location updates shared by other watch apps and checks recent provider caches captured after workout start/resume. Routes reject stale/mock/poor/reordered fixes and split across gaps/pauses. Retry restarts provider subscriptions; stalled subscriptions recover after two minutes. Weak accuracy, location disabled, permission denied, and unavailable hardware have separate statuses. Stride-derived distance and calories are estimates. New workouts carry distance provenance; old workouts with no source remain labeled unknown.
- Workout heart-rate plots use actual accepted records in the workout's time interval. An average BPM cannot reconstruct time in zones, peaks, recovery, or a distribution. Resting BPM and readiness are not inferred from a daily minimum/default age.
- Hydration contains actual user-entered amounts, starting at zero; it is a manual watch log. Breathing has a timed/haptic guide and produces no health measurement.
- Activity reminders require observed monitoring coverage and step evidence. They do not assert that the user sat, stood, or wore the watch.
- Compass heading requires an orientation sample and describes magnetic north/calibration. Altitude requires a fresh valid location sample. Unsupported/missing states remain empty.

Health values retain their original capture time. A saved reading is not a live reading. Phone disconnection is visible; unavailable fields are distinct from a captured zero.

Android does not grant HealthSync access to another app's private database simply because both apps are installed. If the Kolabee U8 Ultra's **Heart rate** or **Blood oxygen** app does not expose a compatible export, HealthSync reports that limitation and offers **Open watch health app**. After measuring there, **Check watch app data** retries the shared export. No root access, private-file reading, fabricated readings, or assumed timestamps are used. See [WATCH-HEALTH-FALLBACK.md](WATCH-HEALTH-FALLBACK.md) for the export contract and device checks.

## Upgrade cleanup

Earlier versions generated a default sleep night, unsupported sleep stages, random/manual oxygen values, synthetic workout heart-rate distributions, and recovery defaults. The 2.7.0 phone migration moves historical sleep/oxygen rows whose genuine capture provenance cannot be established into local unverified archive tables, excluding them from active history. The watch resets legacy sleep defaults and only restores new-schema accepted sensor caches. Its old hydration total/date are retained under unverified preference keys while the new manual log starts empty. Phone archives retain the unverifiable records locally; the apps do not infer replacements.

Phone database versions 4 and 5 preserve workouts, step history, notification history, and accepted heart-rate history. The watch archives its unverifiable legacy live step cache and starts a new validated live count; stored historical step rows and buckets are preserved. Unreliable heart-rate rows are excluded from health queries. Install both new APKs so the phone and watch use the same provenance contract.

Phone database versions 1–3 have no exported or documented schema. Explicit preservation migrations rename their original application tables to local `legacy_v1_*`, `legacy_v2_*`, or `legacy_v3_*` archives, retaining rows, columns, keys, indices, and triggers. Other original tables remain untouched. The app creates the verified current schema with empty active history and does not infer old records' meaning or provenance. Room updates its own metadata after validation. No destructive fallback is enabled, and these archives are never exported as captured health records.

Untouched weight/height entries stay blank. Calculation defaults are disclosed as assumptions and do not appear as personally entered body measurements. Old automatic profile values cannot establish that the user entered them.

The 2.7.1 phone database migration from version 6 to 7 adds health-app provenance columns with defaults for existing sensor records. It preserves existing active health history and all archive tables. Previous migration paths continue through version 7 without destructive fallback.

## Physical acceptance

1. Install both APKs as updates over 2.6.0. Confirm preserved workouts/steps and removal of unsupported oxygen/sleep defaults. Fresh installs must show empty health states.
2. Verify Back, Clock/Home, four swipe panels, all app-grid routes, Settings, face selection/creator, notification source filters, workout resume/details/export, and no inaccessible controls on the actual circular display.
3. Inspect sensor inventory and permissions. Missing sensors must say unavailable; denied permission must give a working settings route. Android 13+ background body-sensor access is separate from foreground access and may be restricted by the installer/OEM.
4. Measure heart rate and oxygen while worn, then off wrist. Confirm original capture timestamps, rejection of unreliable values, no invented timeout result, bounded acquisition, and no synthetic BPM from raw PPG. Compare against known reference hardware without claiming calibration from software validation.
5. Walk a counted route. Check no double counting from hardware counter+detector, reset/day rollover, no accumulation from rejected timestamps, and actual activity-reminder coverage.
6. Test all eight workout types, pause/resume/process recovery, sensor gaps, GPS quality, distance-source labels, estimated calories, route segmentation, CSV/GPX, and watch-to-phone acknowledgement/retry.
7. Leave overnight with the required sensor/background access. Confirm rest remains empty without sufficient motion coverage, no fabricated stages/score, observation-gap reset, completion after waking, and battery use.
8. Test water log/undo/day rollover and breathing start/stop/background lifecycle. No stress score or automatic water intake should appear.
9. Calibrate compass and test location denied/off/stale/mock/no-altitude cases. Missing heading/elevation must remain empty.
10. Test updates via phone Bluetooth and watch Wi-Fi. Interrupt a transfer; invalid/incomplete bytes must not reach installation. Confirm checksum, package/version, certificate, separate unknown-source permission, verification result, and Android installation consent. Installation success is established by the device's new reported version.
11. Complete call/media/notification/audio tests in [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md), then measure overnight and workout battery behavior.

Automated validation cannot substitute for these hardware checks. No real capture is claimed merely because an APK builds or a unit test passes.
