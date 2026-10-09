# HealthSync Watch 2.4.0 / Phone 2.3.0

Prepared 9 October 2026. Install HealthSync-Watch-2.4.0-Android8.apk on the watch and HealthSync-Phone-2.3.0.apk on the phone. Watch code 16 supports Android 6.0/API 23 and newer, including Android 8. Phone code 10 requires Android 8/API 26 or newer. Both devices must expose Bluetooth Classic RFCOMM for this connection.

Read [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md) for caller names, watch answering, Bluetooth Calls audio, screen-off popups and confirmed replies. Full call/audio controls on Android 12+ phones require the explicit watch association in HealthSync Settings → Watch Calls; legacy phone answering stays available with call permission.

## Install and connect

1. Transfer each APK to its device and open it in the file manager. Allow installation from that file manager if Android requests it.
2. Install as an update over the existing HealthSync app to retain data. The packages use the existing local development signing configuration.
3. Open HealthSync Watch normally. Grant permissions for the features you use. Try the clock, Apps and **Settings → Android settings** before selecting it as Home.
4. Pair the devices in Android Bluetooth settings and open both HealthSync apps. Use the phone's Device tab to connect; a manual disconnect stays paused until Connect is selected.
5. Enable HealthSync Notification Access on the phone for phone alerts and media controls. Allow phone app notifications for connection status and the Find phone Stop sound control.
6. For notifications from other watch apps, separately grant **Watch Settings → Watch notifications → Allow watch notification access**. **Manage in launcher** starts enabled but only works once that listener is granted and connected. The watch's notification-posting permission is also needed for its own alerts, including timer completion.
7. To mask corners over other apps, open **Watch Settings → Software bezel**, enable **Round bezel over apps** and grant **Allow display over other apps**. This optional grant is separate from notification access.

The clock, app grid, tools and saved inbox work without a phone. Music transport and Find phone need a current connection and the updated phone app.

## Optional default Home and recovery

Open **Settings → Home app → Use HealthSync as Home** and follow Android's chooser. On older firmware, use **Choose / revert default Home**, or press the physical Home button and select HealthSync if Android offers the chooser.

Restore the original launcher through the same Home settings page. Keep that launcher installed. **Open another Home app** opens it temporarily without changing your default. Android Settings remains available through HealthSync settings, the quick-controls Settings tile's hold action, and the app grid where firmware exposes it.

Home selection is optional. Firmware can restrict the chooser or omit Android settings screens. Default Home does not keep HealthSync unkillable or grant vendor privileges.

## Watch navigation

- Swipe **up** for Apps, **down** for Quick controls, **left** for Notifications and **right** for Fitness.
- Panels follow the swipe and settle within Home. Back or a left-edge back gesture returns through panel history. Home returns to the clock.
- Use the bottom clock shortcuts for visible routes when enabled. Hold the clock for face previews; swipes remain available with shortcuts hidden. Dashboard scrolls normally; start vertical launcher swipes at its top/bottom edge and use **Settings → Watch faces** for its picker.
- Apps has All, Pins (favorites) and Recent tabs with installed app icons and built-in Health, Music, Notifications, Tools and Settings. Tap to open; hold to pin/unpin or open app information. External apps use their own Android tasks.

## Faces, display and wrist wake

Choose **Settings → Watch faces** for sixteen choices: Orbit, Analog, Minimal, Casio Circular, Sport, Blueprint, Solar, Typography, Terminal, Full Analog, Roman, Pure Digital, Casio Pure, Health Rings, Classic LCD or Dashboard. Casio Circular uses a round retro bezel, seven-segment LCD clock and live health/status readings. Display & AOD offers the same choices. System wallpaper uses Android's wallpaper behind interactive faces except Classic LCD; the dim clock stays black. Choose wallpaper opens a picker if the firmware supplies one.

For a face without the bottom icons, select **Full Analog**, **Roman**, **Pure Digital** or **Casio Pure**; those four always hide the shortcut row. To remove bottom shortcuts from any of the other twelve faces, open **Settings → Display & AOD** and turn off **Clock shortcuts**. This switch starts on. With shortcuts hidden, swipe up for Apps, down for Quick controls, left for Notifications or right for Fitness; hold the clock to change faces. Dashboard's picker remains available through Settings.

AOD starts off. Enable it in Quick controls or **Settings → Display & AOD**. Its eight styles are Match watch face, Digital, Analog, Outline, Stacked, Retro LCD, Dial Rings and Date Focus. Choose a 15, 30 or 60 second idle delay and 1%, 3%, 6% or 10% brightness. Defaults are Match watch face, 15 seconds and 3%. It updates at minute boundaries and shifts lit pixels. The first tap brightens without activating a control underneath. Disable it for normal timeout.

**Raise wrist to brighten** is a separate opt-in under Display & AOD. It uses an available non-wakeup motion sensor only while the foreground dim clock is visible, stopping in bright mode, other panels/apps and screen-off. It brightens that clock; it does not wake a physically powered-off screen. Detection needs testing on the actual watch.

This is foreground AOD, not factory low-power mode. The power button, lock screen and firmware retain screen-off control. Opening another app releases display retention. Check watch battery use before relying on AOD daily.

## Software bezel

**Settings → Software bezel** runs a foreground-service mask with a transparent circular center over other apps. Choose 100%, 95%, 90% or 85% circle diameter. Touches pass through; the mask hides corners without resizing app layouts. Controls drawn outside the circle may be hidden.

Android 8 uses an opaque black mask. Android 12 and newer use a translucent mask within Android's allowed touch-opacity limit so touches can reach other apps. Protected screens can hide overlays. Android keeps a mandatory service indicator while the bezel is active. Turn it off in the same settings page or with **Turn off** in its service notification. If foreground-service startup or overlay permission is restricted by firmware, reopen the settings page to review its status.

## Quick controls

The shade shows actual battery, connection and tile states. Wi-Fi toggles where permitted on older Android; Android 10/API 29 and later opens controls. Bluetooth toggles on supported older versions; Android 13/API 33 and later opens settings. Hold either tile for settings. Restricted changes fall back to available Android screens.

DND needs a separate notification-policy grant; tap its tile for consent when required. Volume changes actual media, alarm and ring streams, subject to Android/DND restrictions. Airplane mode and battery saver open system settings.

Brightness changes HealthSync's foreground window. **System brightness** restores Android's value; other apps retain their own/system brightness. Light turns the display white at full foreground brightness and restores the previous state on close or pause.

## Notifications and phone controls

The inbox labels messages **Phone** or **Watch**, saves read/unread state and retains up to 100 entries for seven days. Messages stay readable offline.

On the phone, **Settings → Notification Apps** and **Alerts → Filters** share the complete app catalog, including preinstalled/system apps such as Gmail. Search by app label or package name. Alerts also has All apps, System and Selected views; use All apps if a category hides your search result. System services without launcher icons and remembered notification sources are included. When All Apps is on, every source is already allowed. Turn it off to choose Gmail or other individual sources. Refresh after installing/enabling an app. Selection is possible before granting access, but mirroring needs HealthSync enabled in Android's Notification Access screen. That Android screen lists readers such as HealthSync, not source apps such as Gmail. Apps in a separate profile may not be visible to HealthSync in your current profile. This sideload build uses broad package visibility for the inventory. A future app-store delivery would need review of the `QUERY_ALL_PACKAGES` permission.

On the watch, **Manage in launcher** saves supported clearable alerts before canceling Android's copy. Bluetooth prompts appear when exposed as notifications, and the panel can offer their available native buttons. Ongoing/non-clearable alerts, group summaries and prompts with delete callbacks, remote-input actions or full-screen intents stay with Android. System heads-up can briefly appear; Android permission dialogs and protected screens remain system controlled. Open/actions may expire after source cancellation or process restart. Saved text remains readable; Open app is offered where the source app has a launch entry.

New mirrored messages can open a popup and wake supported unlocked watches even with launcher management enabled. On newer Android, background delivery uses private high-priority banners. Watch notifications settings control popup, wake, sound/vibration and incoming call screens. Captured watch messages do not offer remote replies.

**Clear all** clears across all filters. The panel button clears directly; the separate searchable inbox asks for confirmation. It removes saved phone copies and clearable watch alerts while retaining active ongoing/non-clearable watch alerts and mandatory HealthSync service indicators. It leaves original phone notifications available.

Only active phone messages with a freeform reply action can offer a reply. Sending waits for a phone response. Reply sent to app confirms Android accepted the originating app action; network delivery remains app-controlled. Errors and unknown timeouts are explicit; expired or filtered targets disable replies.

**Apps → Music** or **Settings → Phone controls** shows phone-confirmed track, artist, playback and battery. Previous/play/pause/next needs a compatible active phone media session and phone Notification Access. Offline, unavailable or pending controls stay disabled. Queuing a command does not establish successful playback.

Find phone rings a connected phone for up to 15 seconds. Stop from the watch or the phone notification. Allow phone app notifications and audible alarm volume; DND/sound settings still apply. Ringing appears on the watch after the phone confirms it. Both updated apps are required.

Notification-posting permission, Notification Access and DND policy access are separate grants.

## Tools and fitness

**Apps → Tools** provides a native timer and stopwatch with saved state and stopwatch laps. Timer pause/resume/stop and finish alerts work independently of the tools screen. Allow watch app notifications and its Timer finished channel for sound/vibration alerts. Newer Android exact-alarm access improves precision; otherwise the UI identifies an approximate alert. Stopwatch reopening preserves state; reboot downtime is not counted. Alarm opens the installed Android alarm app if available.

Fitness, all eight workouts, history, manual measurements, independent sensor intervals and profile/goal settings remain. Readings use exposed real sensors. Calories and stride-derived distances are estimates. Actual sensors, GPS, telephony and Bluetooth audio need device testing.

### Heart rate and tracking checks

1. Update both apps, then open **Health → Measure heart rate** on the watch. Before sensor access is granted, the same button says **Allow sensor access** and opens the permission request. The app discovers/selects the sensor again for each attempt and uses one preferred heart-rate sensor. Wear the watch snugly and stay still. The sensor unregisters immediately after the first valid sensor reading or reliable measurement; an attempt without a valid reading ends within 60 seconds. Repeated Measure taps do not extend that attempt's original deadline. Technical details distinguish access, registration failure, no events, no wrist contact, unreliable accuracy and rejected samples.
2. If this firmware reports an in-range heart-rate value with accuracy 0, Health, Fitness and the connected phone Dashboard show it labeled **Sensor reading**. Existing heart-rate complications on watch faces show its BPM value too; some clean and analog faces intentionally omit heart rate. The last sensor reading and reliable sample can remain visible with their original capture time and age for up to 24 hours. The watch saves sensor display feedback separately from health records; it never enters health history or workout statistics. The phone keeps sensor feedback in memory, clears it on disconnect and receives the watch's last reading on reconnect with its original timestamp. Technical accuracy status remains in heart-rate details. Accuracy -1/no contact, invalid values and cached, stale, future or reordered capture times do not produce new readings. Workouts accept only reliable samples at most two minutes old; repeated replay does not add duplicate health measurements.
3. Start an outdoor workout and grant **Location** on the watch. On Android 12+, allow precise location. Tap the workout location status if access or Location is off, then return to the workout. Enabling GPS or granting access now resumes provider registration without restarting the session. Move outdoors for actual satellite acquisition.
4. The status distinguishes permission/precise access, Location off, missing provider, searching, stale fix, GPS fix and network location. GPS routes require fresh, ordered coordinates with known accuracy no worse than 50 m. Rejected fixes, signal gaps and pauses split the route instead of adding a guessed connecting line. The phone map and GPX export keep those breaks.

Set automatic readings in watch **Settings → Sensors** or phone **Settings → Sensor Intervals**, then select **Apply to Watch** on a connected phone. Both devices offer 30 seconds, 1, 5, 10 or 30 minutes, 1 hour and Off; defaults are heart rate every minute and oxygen every five minutes. Watch changes apply immediately and sync back to the phone. Choices reconcile on reconnect, keeping the latest saved edit when device clocks are correct. Reconnecting and changing unrelated settings preserve an unchanged timer's next deadline. The first automatic reading waits for the selected interval; manual Measure rebases the next automatic reading. Off stops automatic acquisition and leaves manual measurement available. Timeouts skip missed slots without queuing extra attempts, and workouts no longer request additional heart-rate acquisitions every 15 seconds.

Wakeup alarms request scheduled measurements while the watch sleeps; the measurement wake lock stays released between samples. Android Doze can delay the selected interval during deep idle. Newer Android uses approximate alarms when exact-alarm access is unavailable.

Accepted heart-rate measurements still require accuracy 1..3, 25..240 BPM and a fresh, ordered capture timestamp from the current acquisition. Accuracy-0 readings follow a separate display path. Receiving a valid reading unregisters only that measurement's sensor and releases its wake-lock budget; another measurement can continue independently. Diagnostic value and timestamp counters run independently of accuracy, so an unreliable frame's other problems are visible. Phone ingestion checks required JSON fields, timestamps, ranges, session duration and sleep stage totals before saving/acknowledging valid records. These checks validate the recorded data path; they cannot calibrate vendor hardware or turn estimates into measured values. Actual battery use still needs measurement on the watch.

If heart rate remains blank, open **Health → Heart rate details** on the watch after an attempt. On a connected phone, open Dashboard → **Heart-rate measurement details → Copy report**, then paste that report into this chat. It records the watch model/API, sensor access, exposed sensor metadata, registration outcomes, independent rejection counts and the number of sensor previews. Main cards use **Sensor reading**; detailed accuracy information remains in the details screen and diagnostic report. The diagnostic report excludes raw BPM values; the details screen may separately display the preview. The report helps identify firmware timestamp/accuracy behavior without guessing an integration.

## Verify and diagnose

SHA256SUMS-Android8.txt lists current APK hashes. HealthSync-Android8-2.4.0-Both-APKs.zip packages both updated apps, setup guides and verification report. Consult BUILD-REPORT-Android8-2.4.0.md and validation/communication-2.4.0/ for final checks. No physical watch or phone was connected during this patch; caller identity, answering, two-way watch audio and actual screen-off alerts require the checks in COMMUNICATION-SETUP.md. Earlier APKs/reports apply to earlier builds.

With USB debugging enabled and watch authorization granted, obtain its serial from adb devices. From the workspace, pass the current APK explicitly:

~~~powershell
.\tools\Install-Watch-Diagnostic.ps1 -Serial 'WATCH_SERIAL' -ApkPath '.\dist\HealthSync-Watch-2.4.0-Android8.apk' -CheckStartup
~~~

From an extracted bundle:

~~~powershell
.\Install-Watch-Diagnostic.ps1 -Serial 'WATCH_SERIAL' -ApkPath '.\HealthSync-Watch-2.4.0-Android8.apk' -CheckStartup
~~~

The helper preserves app data and does not select Home. See Install-Watch-Diagnostic.md for details. Physical acceptance includes Home selection/recovery, notification grants, phone transport, timer alerts, wrist detection, sensors, workouts, calls and AOD battery use.

For a sensor report over USB, use the separate read-only `Collect-Watch-Sensor-Diagnostic.ps1` helper after tapping Measure. See `Collect-Watch-Sensor-Diagnostic.md`. It does not install, grant permissions, clear data or clear logs.
