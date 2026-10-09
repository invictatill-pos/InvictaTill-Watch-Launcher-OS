# HealthSync — Watch 2.4.0 / Phone 2.3.0

HealthSync combines an Android watch Home launcher with fitness sync, workouts, notifications and connected phone controls. This 9 October 2026 delivery strengthens communication: caller names, phone-confirmed answer/decline/end/mute and audio routing, separate incoming call alerts, screen-off message popups on compatible Android 8 watches, private message banners, quiet reconnects, conversation details and confirmed reply results. Android 12+ phones use explicit companion watch association for full call controls. Legacy Android 8–11 answering remains supported where the public APIs and permissions permit it. Talking through the watch requires its firmware's Bluetooth Calls connection; HealthSync's data connection cannot create that audio capability. See [communication setup](docs/COMMUNICATION-SETUP.md).

Existing heart-rate display, acquisition timing, tracking validation, sixteen faces, eight AOD styles, watch tools, source filters, fitness history and software bezel remain available.

## Install

| Device | APK | Version code | Minimum Android |
| --- | --- | --- | --- |
| Watch | dist/HealthSync-Watch-2.4.0-Android8.apk | 16 | Android 6.0 / API 23 |
| Phone | dist/HealthSync-Phone-2.3.0.apk | 10 | Android 8.0 / API 26 |

Install both APKs as updates to preserve existing data and enable the complete communication protocol. Open the watch app normally and try the clock, Apps and Android Settings before selecting it as Home. Home selection is optional: **Settings → Home app → Use HealthSync as Home** opens Android's chooser. The same page restores the original launcher or opens another Home app temporarily.

Pair the devices in Android Bluetooth settings, open both HealthSync apps and grant permissions for the features you use. On the phone, use the Device tab to connect. A manual disconnect stays paused until Connect is selected. Sync requires Bluetooth Classic RFCOMM on both devices; firmware determines which sensors and controls Android exposes.

The watch APK is a debug build with v1/v2 signing for older Android watches. Both packages use the existing local development signing configuration. See [dist/ANDROID8-INSTALL.md](dist/ANDROID8-INSTALL.md) for installation, checksum and report references.

## Watch shell

| From the clock | Opens |
| --- | --- |
| Swipe up | Apps |
| Swipe down | Quick controls |
| Swipe left | Notifications |
| Swipe right | Fitness |
| Hold the clock | Watch face picker |

Panels follow the opening swipe and settle into place. Back and the left-edge back gesture return through watch navigation. Home returns to the clock. **Clock shortcuts** adds visible bottom buttons on supported faces and starts on; swipes remain available when those buttons are hidden. Dashboard keeps its scrolling; start vertical launcher swipes at its top/bottom edge and use **Settings → Watch faces** for its picker.

- **Faces:** Sixteen choices: Orbit, Analog, Minimal, Casio Circular, Sport, Blueprint, Solar, Typography, Terminal, Full Analog, Roman, Pure Digital, Casio Pure, Health Rings, Classic LCD and Dashboard. Casio Circular has a round retro bezel and seven-segment LCD clock. Casio Pure has a textured round black dial and recessed LCD with live seconds and date; its printed dial labels are decorative. **Full Analog, Roman, Pure Digital and Casio Pure** always omit the bottom shortcut row. To hide it on the other twelve faces, turn off **Settings → Display & AOD → Clock shortcuts**. Swipe up/down/left/right for Apps/Controls/Notifications/Fitness and hold the clock for the picker. Optional System wallpaper uses Android's selected wallpaper behind interactive faces except Classic LCD; the dim clock uses black.
- **Apps:** The icon grid includes installed launcher apps and built-in Health, Music, Notifications, Tools and Settings. All, Pins (favorites) and Recent tabs keep shortcuts accessible. Hold an app to pin/unpin it or open Android app information. External apps run in their own Android tasks.
- **Fitness:** Steps, goal progress, heart-rate readings with their age and workout routes stay close to the clock. Fresh accuracy-0 values use the **Sensor reading** label; technical accuracy information is in heart-rate details. Workout sampling uses recent reliable readings. All eight workouts remain: Walk, Run, Cycling, Basketball, Home workout, Badminton, Cricket and Yoga. Active sessions can resume; history stays on-device.
- **Settings:** Compact categories open detailed sensor intervals, measurements, profile/goal, connection, display, permissions, Home recovery and device information pages.

## Quick controls

The shade shows actual watch battery/connectivity, circular controls, volume sliders and foreground brightness. Wi-Fi and Bluetooth toggle directly where Android permits it. Android 10/API 29 and later opens Wi-Fi controls; Android 13/API 33 and later opens Bluetooth settings. Older firmware can also refuse a change and fall back to settings. Hold either tile for its settings.

DND toggles only after the user grants Android notification-policy access. Its first tap opens consent when needed. Media, alarm and ring sliders use actual watch audio streams; firmware/DND restrictions can require Android sound settings. Airplane mode and battery saver open system settings.

Brightness applies to HealthSync's window. **System brightness** restores Android's value; other apps use their own/system brightness. Light is a white-screen flashlight that restores brightness and display retention on close or pause. AOD can be switched here or in Display & AOD.

## Notifications and phone controls

The inbox combines **Phone** and **Watch** sources with read/unread state, timestamps, source labels, expanded messages and dismiss controls. Up to 100 entries are retained for seven days, including while disconnected.

- **Phone notifications:** Enable HealthSync Notification Access on the phone. Its mirroring switch and app filters determine what reaches the watch over Bluetooth. **Alerts → Filters** includes **All apps**, **System** and **Selected** views, with search by label/package. The inventory includes system services without launcher icons and remembered notification sources. The All apps switch includes system alerts; turn it off to select individual sources. This sideload delivery uses broad package visibility for that inventory; a future app-store release would need a separate review of its `QUERY_ALL_PACKAGES` permission.
- **Watch notifications:** Separately grant access through **Settings → Watch notifications → Allow watch notification access**. **Manage in launcher** starts enabled and takes effect only when the watch listener is granted and connected. Supported clearable notifications are saved in the inbox before Android's copy is canceled. Bluetooth alerts can appear with their available native action buttons when Android exposes them as notifications. Android retains ongoing/non-clearable alerts, group summaries and prompts with delete callbacks, remote-input actions or full-screen intents; heads-up alerts may briefly appear. Native open/actions may expire after source cancellation or process restart. Saved text remains, with an Open app recovery route where available.
- **Alert delivery:** New messages can wake an unlocked Android 8 watch and open their details even with launcher management enabled. Watch notifications settings separately control popups, screen wake, channel sound/vibration and incoming call screens. Newer Android watches use a private high-priority system banner where background launch is restricted. DND, locks and disabled channels are respected. Messages never replace an active call screen; reconnects, unchanged content and background progress are quiet.
- **Clear all:** The panel button clears the entire inbox; the separate searchable inbox asks for confirmation. Both clear across filters, remove saved phone copies without canceling the original phone notifications, dismiss clearable watch alerts and preserve active ongoing/non-clearable watch alerts. HealthSync's mandatory foreground-service indicator remains.
- **Replies:** Only current phone notifications with a freeform reply action can offer a reply. Sending waits for a correlated phone response; Reply sent to app means Android accepted the source app action, not confirmed network delivery. Errors are explicit; unconfirmed replies say to check the phone. Removed/filtered notifications and disconnected access revoke stale targets while saved text remains readable.
- **Calls:** Grant phone/contacts access and enable Settings → Watch Calls on the phone. On Android 12+, associate the currently connected watch in Android's dialog for Telecom controls. Caller name/number, current call duration, answer/decline/end, mute and audio routes reflect phone-confirmed state. Answering from the watch requests its exact supported Bluetooth call device after confirmation when available. Other headsets are identified separately. Legacy Android 8–11 phones retain permission-gated answer controls and API28+ decline/end; ambiguous multiple calls disable controls. See [setup and hardware checks](docs/COMMUNICATION-SETUP.md).
- **Music:** **Apps → Music** or **Settings → Phone controls** shows phone-confirmed track, artist, playback and battery, with previous/play/pause/next controls. Enable phone Notification Access for media-session access and start a compatible phone player. Pending/offline/unavailable controls stay disabled; queuing a command is not reported as success.
- **Find phone:** Ring a connected phone and stop from the watch or the phone's Stop sound notification. The alert stops automatically after 15 seconds. It respects alarm volume and sound/DND settings and needs phone notification permission. The watch shows ringing only after a phone response confirms it.

Notification-posting permission, Notification Access and DND policy access are separate Android grants.

## Tools, AOD and wrist wake

**Apps → Tools** provides a native timer and stopwatch with saved state and stopwatch laps. The timer supports pause/resume/stop and a finish notification after leaving the tools screen. Allow HealthSync app notifications and its Timer finished channel for alerts. Newer Android exact-alarm access controls timing precision; without it the UI reports an approximate alert. Stopwatch state survives reopening/process recreation and does not count unknown reboot downtime. **Alarm** opens the installed Android alarm app when available.

AOD starts off. **Display & AOD** offers eight dim styles: Match watch face, Digital, Analog, Outline, Stacked, Retro LCD, Dial Rings and Date Focus. Choose a 15, 30 or 60 second idle delay and 1%, 3%, 6% or 10% foreground brightness; defaults are Match watch face, 15 seconds and 3%. The dim clock uses black, updates at minute boundaries and shifts lit pixels. The first tap brightens it without activating a control underneath. Optional **Raise wrist to brighten** uses an exposed non-wakeup gravity/accelerometer sensor only while the foreground dim clock is visible. It stops while bright, in other panels/apps and screen-off. Wrist orientation/detection needs physical watch testing.

**Settings → Software bezel** offers a foreground-service overlay with a transparent circular center and a black mask outside it. Enable **Round bezel over apps** and grant **Allow display over other apps**. Choose a 100%, 95%, 90% or 85% circle diameter. Touches pass through and other apps continue to run normally. The mask hides corners without resizing apps, so controls outside the circle may be hidden. It is opaque on Android 8; Android 12 and newer use a translucent mask within Android's touch-opacity limit. Protected screens can hide overlays. Turn it off in settings or through its service notification's Turn off action; Android keeps that service indicator while the bezel runs.

This launcher runs on ordinary Android. It does not replace firmware, provide factory low-power AOD, provide firmware-level wrist wake, or grant private watchface/system privileges. Incoming communication uses bounded event-driven wake requests where Android permits. Default Home does not prevent Android stopping the process. Returning to the clock retries eligible services; boot/update recovery respects permissions.

## Health and sync

Existing phone dashboards, workout details/search, CSV/GPX export, notification history and validated profile settings remain. Watch checkpoints recover interrupted sessions paused. Active duration, cadence, distance and repetitions exclude pauses; manual repetition correction remains available.

Sensor values come from exposed hardware. A reliable heart-rate measurement requires accuracy 1..3, 25..240 BPM and a fresh, ordered capture timestamp from the current acquisition. On firmware that marks its values accuracy 0, Health, Fitness and the connected phone Dashboard can show an in-range value labeled **Sensor reading**. Watch 2.3.4 also supplies these readings to all existing heart-rate complications as ordinary BPM values. Some clean and analog faces intentionally omit heart rate. Both reliable readings and sensor display feedback remain visible with their age for up to 24 hours. The watch saves display feedback separately from health records so it survives reopening; it never enters health history or workout statistics. Workout statistics require a reliable sample no more than two minutes old. The phone keeps sensor feedback in memory and clears it on disconnect; reconnect retrieves the watch's last reading with its original capture time. Accuracy -1/no contact, invalid values and invalid capture times do not produce sensor display feedback. Technical accuracy status remains available in heart-rate details; the display change preserves the existing measurement-validation rules.

Set automatic readings in watch **Settings → Sensors** or phone **Settings → Sensor Intervals → Apply to Watch**. Both offer 30 seconds, 1, 5, 10 or 30 minutes, 1 hour and Off. Defaults are heart rate every minute and oxygen every five minutes. The first automatic heart-rate attempt waits for the selected interval. It stops immediately after its first valid reading, unregisters the heart-rate listener and releases that measurement's wake-lock budget while leaving any other active measurement's budget intact. A failed attempt stops after at most 60 seconds, skips missed timer slots and does not queue a backlog. Manual Measure resets the next automatic deadline. Off stops automatic acquisition while keeping manual measurement available; workouts use the same schedule and do not force additional 15-second heart-rate attempts.

Wakeup alarms request the next measurement without keeping a measurement wake lock held between samples. Android Doze can delay the selected interval during deep idle. On newer Android, unavailable exact-alarm access uses an approximate alarm fallback.

Tap **Health → Measure heart rate**, wear the watch snugly and stay still. Repeated Measure taps reuse the active acquisition without extending its 60-second deadline; a later request starts a new attempt after cleanup. **Heart rate details** distinguishes registration failure, no events, no contact, unreliable accuracy and rejected values/timestamps. Its value and timestamp counts remain visible even when the same frame also fails accuracy. Missing measurements show an explicit sensor/access/acquisition status. Vendor oxygen sensors must identify as SpO2/blood oxygen. Capture-time, reliability, route-quality and incoming-record validation reject invalid tracking data. Calories and stride-derived distance are estimates. GPS, call actions and audio routing depend on hardware and Android permissions.

Bluetooth frames are bounded and serialized. Saved workouts and closed step buckets retry until the phone acknowledges persistence; daily aggregation avoids duplicate bucket counting. Profile and goal apply again after reconnecting; interval choices reconcile from either device without restarting an unchanged countdown. Health history stays on-device and automatic Android backup/transfer is disabled.

## Build and verification

Use JDK 17 or newer and Android SDK platform/build-tools 34. Set sdk.dir in local.properties. The wrapper uses JAVA_HOME, or Android Studio's bundled runtime when available.

~~~powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug
.\gradlew.bat :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug
~~~

Outputs are under PhoneApp/app/build/outputs/apk/release/ and WatchApp/app/build/outputs/apk/debug/. Packaged APKs and hashes are in dist/.

Current communication evidence is in validation/communication-2.4.0/, with the delivery scope and final verification recorded in dist/BUILD-REPORT-Android8-2.4.0.md. No physical watch or phone was connected for this patch; screen wake, companion association, calling, two-way audio, notification actions and battery behavior require the device checks in docs/COMMUNICATION-SETUP.md. Earlier heart-rate and launcher validation folders remain available for their original builds.

The roadmap and hardware checklist are in [docs/LAUNCHER-ROADMAP.md](docs/LAUNCHER-ROADMAP.md). Home recovery, notification grants, Bluetooth/media/find-phone behavior, timer alerts, wrist detection, vendor sensors, GPS, calls and overnight battery use require testing on the actual devices.
