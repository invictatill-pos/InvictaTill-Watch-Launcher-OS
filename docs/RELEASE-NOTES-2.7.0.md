# HealthSync 2.7.0 — Visual refresh and real capture

HealthSync now uses a consistent visual style throughout the phone and watch, with clearer navigation, readable controls, useful empty states, and health values grounded in captured records.

## Look and navigation

- The phone uses a warm cream and forest palette, consistent headings, calmer cards, larger controls, and readable charts. Today, Training, Faces, Device, and Settings remain directly accessible.
- Watch menus, health pages, settings, workouts, controls, media, notifications, and face selection share dark surfaces, mint actions, grouped content, and visible Back/Clock routes.
- Permission feedback refreshes after returning from Android Settings. Workout details handle loading and missing records. Notification history has a visible return route.
- Face creation previews the selected ambient layout, hands, markers, widgets, dates, and gradients. Example health values are confined to an explicitly labelled design mode.

## Health records

- Heart rate and oxygen require an appropriate sensor, accepted accuracy/value, and a valid original capture timestamp. Unsupported hardware and failed acquisition stay unavailable.
- Removed generated oxygen values, default sleep nights/stages, stress/recovery scores, simulated ECG graphics, and workout heart-rate distributions reconstructed from averages.
- Rest duration is explicitly a motion estimate. Steps may include a motion estimate; calories and stride-derived distance remain labelled estimates. Hydration is a manual log. Breathing is a timed guide without a generated health score.
- Steps remain unavailable until a validated sensor observation establishes a baseline or records movement. Counter and detector streams cannot double count the same movement. Clock faces no longer start with invented health readings.
- Workout charts use actual accepted heart-rate records. Location routes reject invalid fixes and break across gaps. History and exports expose distance/calorie provenance.
- Untouched weight/height fields remain blank; calculation defaults are disclosed as assumptions.

## Reliable delivery

- Updates require complete metadata, HTTPS, SHA-256 verification, the expected package/version, and a compatible installed certificate.
- Watch transfers validate chunk geometry, correlation, completion, interruption, and timeout. The phone distinguishes transfer/verification from Android installation consent.
- Face installation waits for an acknowledgement and correlates requests on updated watches. A queued request or timeout does not claim success.

## Upgrade notes

Install **both phone and watch APKs** over the existing apps. The release retains the existing signing identity. Keep the apps installed to preserve their local history.

The phone moves unverifiable legacy oxygen and sleep records into local unverified archives outside active history. Saved workouts, step history, notifications, and accepted heart-rate records are retained by the known database migrations. For unknown database schemas before version4, original tables are archived intact and active history starts fresh; their health meaning is not guessed. The watch preserves stored step history and archives its unverifiable live step cache; today's live count restarts after upgrade and remains unavailable until a valid observation. Legacy hydration totals that may contain an automatic baseline are retained as unverified preferences while the new manual log starts empty.

Sensor accuracy, actual circular-display fit, overnight tracking/battery, Bluetooth call audio, and OEM permission behavior still require physical-device testing. No phone or watch was attached during development. See the included build report and [health acceptance checklist](https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/blob/v2.7.0/docs/HEALTH-DATA-CONTRACT.md).
