# Health data contract — 2.7.0

HealthSync displays captured records, clearly marked estimates, and explicitly manual entries. It never substitutes a plausible reading when hardware is unavailable or acquisition fails.

## Capture and display rules

- Heart-rate history requires BPM 25–240, accuracy 1–3, and a fresh, ordered original sensor timestamp from the active acquisition. Android heart-rate sensors and explicitly identified vendor BPM metrics qualify. Raw optical values, PPG waveforms, and heartbeat events do not. Unverified sensor feedback belongs only in diagnostic display, never workout statistics/history.
- Oxygen requires an explicit vendor saturation metric, reliable accuracy, 50–100%, and a valid original sensor timestamp. The first accepted capture ends acquisition. Failed acquisition ends within the measurement timeout. The phone accepts only oxygen records declaring the new watch sensor source.
- Repeated cached frames, invalid timestamps, NaN/infinite values, unreliable accuracy, and invalid ranges cannot create a new health record. Replacing an invalid capture timestamp with callback delivery time is forbidden.
- Only one hardware step source counts at a time. Accelerometer estimates remain estimates. Daily totals and closed buckets synchronize without duplicate aggregation. App startup and midnight cannot establish a captured zero; steps remain unavailable until a validated sensor observation establishes a baseline or records movement. Counts describe recorded steps during monitoring, rather than promising complete day coverage.
- Motion rest requires continuously observed minute epochs and resets on sensor gaps/clock discontinuity. Rest onset/finalization requires real observation coverage. It carries source `WATCH_ACTIGRAPHY`, an estimated duration, zero stage fields, and `stagesAvailable=false`. Stillness cannot confirm sleep or identify deep/light/REM stages.
- GPS/network distance is labeled by source. Routes reject stale/mock/poor/reordered fixes and split across gaps/pauses. Stride-derived distance and calories are estimates. New workouts carry distance provenance; old workouts with no source remain labeled unknown.
- Workout heart-rate plots use actual accepted records in the workout's time interval. An average BPM cannot reconstruct time in zones, peaks, recovery, or a distribution. Resting BPM and readiness are not inferred from a daily minimum/default age.
- Hydration contains actual user-entered amounts, starting at zero; it is a manual watch log. Breathing has a timed/haptic guide and produces no health measurement.
- Activity reminders require observed monitoring coverage and step evidence. They do not assert that the user sat, stood, or wore the watch.
- Compass heading requires an orientation sample and describes magnetic north/calibration. Altitude requires a fresh valid location sample. Unsupported/missing states remain empty.

Health values retain their original capture time. A saved reading is not a live reading. Phone disconnection is visible; unavailable fields are distinct from a captured zero.

## Upgrade cleanup

Earlier versions generated a default sleep night, unsupported sleep stages, random/manual oxygen values, synthetic workout heart-rate distributions, and recovery defaults. The 2.7.0 phone migration moves historical sleep/oxygen rows whose genuine capture provenance cannot be established into local unverified archive tables, excluding them from active history. The watch resets legacy sleep defaults and only restores new-schema accepted sensor caches. Its old hydration total/date are retained under unverified preference keys while the new manual log starts empty. Phone archives retain the unverifiable records locally; the apps do not infer replacements.

Phone database versions 4 and 5 preserve workouts, step history, notification history, and accepted heart-rate history. The watch archives its unverifiable legacy live step cache and starts a new validated live count; stored historical step rows and buckets are preserved. Unreliable heart-rate rows are excluded from health queries. Install both new APKs so the phone and watch use the same provenance contract.

Phone database versions 1–3 have no exported or documented schema. Explicit preservation migrations rename their original application tables to local `legacy_v1_*`, `legacy_v2_*`, or `legacy_v3_*` archives, retaining rows, columns, keys, indices, and triggers. Other original tables remain untouched. The app creates the verified current schema with empty active history and does not infer old records' meaning or provenance. Room updates its own metadata after validation. No destructive fallback is enabled, and these archives are never exported as captured health records.

Untouched weight/height entries stay blank. Calculation defaults are disclosed as assumptions and do not appear as personally entered body measurements. Old automatic profile values cannot establish that the user entered them.

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
