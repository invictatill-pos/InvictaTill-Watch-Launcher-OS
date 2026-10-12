# HealthSync launcher status and device checks

Current release: Phone/Watch 2.7.0, 12 October 2026. The current source, README, and build report supersede descriptions in older reports.

## Implemented experience

The watch has five built-in faces plus installed phone-created/catalog faces, four swipe panels, a searchable app grid/favorites/recents, fitness, notifications, quick controls, phone music/find-phone, health capture, workouts/history, compass, hydration, guided breathing, timer/stopwatch/alarm routes, display/AOD, foreground wrist raise, software bezel, and Home recovery.

The phone has Dashboard, Workouts/details/search/CSV/GPX, Device setup, Faces/catalog/creator, Settings/profile/sensor intervals, notifications/history/source filters, companion calls, and verified app/watch updates. Navigation includes visible secondary-screen return routes and recoverable unavailable states.

2.7.0 applies a coherent visual hierarchy and consistent cards/buttons/status messages across both apps. It removes invented health values and labels real captures, estimates, and manual entries appropriately. See [HEALTH-DATA-CONTRACT.md](HEALTH-DATA-CONTRACT.md).

## Firmware and Android boundaries

| Capability | Actual boundary |
| --- | --- |
| Sensor capture | Requires supported exposed hardware, reliable frames, original timestamps, and permissions. An app cannot derive saturation from arbitrary raw PPG or create a sensor absent from firmware. |
| Sleep and stress | Motion rest is an estimate. Deep/light/REM, HRV, stress, readiness, and sleep quality need validated measurement sources before they can be displayed. |
| AOD and wrist raise | Foreground dim clock and motion-triggered brightening; factory display doze and screen-off wrist wake require firmware integration. |
| System controls | Public Android APIs and available settings screens determine Wi-Fi/Bluetooth/audio/DND/brightness behavior. |
| Bezel | Optional overlay hides corners without resizing other apps; protected screens and modern touch-opacity rules apply. |
| Calls/audio | Public/companion Telecom controls require access. Talking through the watch needs supported firmware Bluetooth call audio. |
| Notifications/replies | Separate phone/watch listener grants; native source actions can expire; Android acceptance of a reply action does not confirm network delivery. |
| Home recovery | Home selection is optional and reversible through Android settings. Android can stop the process; being Home grants no private privilege. |
| Updates | Verify bytes/package/version/certificate; Android user consent completes installation. Transmission alone cannot prove installation. |

## Next hardware acceptance

Use actual phone/watch hardware to verify all screen routes, round-display fit, permissions, sensor inventory and off-wrist rejection, counted steps, GPS routes/pauses, overnight motion coverage and battery, notification wake/DND, call controls/audio, media/find-phone, timer alerts, Home recovery, and update consent. Follow the health and communication guides and record the device model/API/firmware/build versions.

No connected real device was available during this source enhancement. Unit tests, lint, signatures, and package checks are software evidence only. Any future factory sensor engine or private firmware integration requires verified interfaces and actual hardware testing before claiming support.
