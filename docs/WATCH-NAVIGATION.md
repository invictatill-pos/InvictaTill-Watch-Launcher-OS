# Google Maps navigation on the watch

HealthSync 2.11.0 adds a dedicated **Navigation** app on the watch. It displays Google Maps' current navigation notification as large instructions, distance to the next maneuver, the supplied maneuver icon, and available road/trip details. Navigation is separate from the optional phone screen viewer.

## Setup

1. Update both apps: phone **2.11.0 / code 34** and watch **2.11.0 / code 39**. Install over the existing packages to preserve data. Connect the paired devices in HealthSync.
2. On the phone, open **Device → Watch navigation → Enable watch navigation**. This preference is separate from ordinary notification mirroring and app filters.
3. If requested, choose **Allow notification access** and enable HealthSync in Android's Notification access settings. Also allow Google Maps notifications in the phone's app settings. Return to HealthSync.
4. Choose **Open Google Maps**, select a destination and **Start** navigation in the installed Google Maps app. A route preview or the Maps website does not provide the ongoing navigation notification needed here.
5. On the watch, open **Apps → Navigation**, or **Settings → Phone connection → Navigation**. Read the large next-turn distance and instruction. Scroll for longer instructions or trip details. Back and Clock stay available.

There is no screen-capture prompt for this flow. Google Maps continues calculating the route and providing spoken directions on the phone. The phone screen may be locked while Maps, notification access and Bluetooth remain operational. The watch needs neither its own Maps installation nor its own internet for these directions. Maps still needs a working route/location source on the phone.

## What the watch shows

- The current instruction and distance come from Maps' public notification fields. An original supplied navigation icon is displayed when available. Clear supported English maneuver phrases also have a direction-symbol fallback; ambiguous text stays without a guessed arrow.
- Road names and trip summary (such as remaining time, distance or arrival time) appear only when Maps includes them. HealthSync does not calculate a position, countdown, route, lane guide or ETA from these fields.
- Source language text is preserved. Localized distance units that cannot be safely separated remain in the instruction. A Maps version that supplies only a custom notification layout may provide too little public text; HealthSync cannot recover missing instructions from private app views.
- Updates are forwarded when Maps changes its notification, with a source-state check every five seconds while connected. Unchanged state does not buzz on each heartbeat or distance update. A brief direction-change vibration depends on watch notification/vibration settings and Do Not Disturb.
- Disconnecting hides the old instruction immediately. Missing updates for 20 seconds replace directions with a delayed-update state. Reconnecting requests the current source state. Ending the route, disabling the feature, or losing notification access clears the route display.

To stop sharing directions, use **Disable watch navigation** on the phone. To end route calculation and spoken directions, stop navigation in Google Maps itself. Leaving the watch Navigation screen does not stop the phone's route.

## Troubleshooting

**Waiting for navigation:** Start a route, not just a preview, and check that Maps has an ongoing notification with navigation text. Allow Maps notifications. Keep HealthSync's Bluetooth connection active.

**Access unavailable:** Enable HealthSync's Notification access, return to the phone app, and reopen Navigation on the watch. OEM battery restrictions or low-memory firmware can disconnect Android's notification listener.

**Directions delayed / phone disconnected:** Check Bluetooth and phone background restrictions. Large file transfers can delay messages. Obsolete queued navigation updates are skipped; the watch clears guidance when current updates stop arriving.

**Update the watch:** Both apps need 2.11.0 for structured navigation. Older watches keep their ordinary notification behavior; the new navigation protocol is sent only to a watch advertising support.

## Optional phone screen viewer

**Device → Phone screen viewer (optional) → Share phone screen** still opens the earlier screen-capture feature. Open **Live phone map** on the watch for that view. It shares phone pixels and requires the phone to stay unlocked. Use **Navigation** for glanceable turn directions. Enabling watch navigation stops an existing screen-sharing session. See [the screen-viewer guide](LIVE-PHONE-MAP.md) for its separate consent and privacy behavior.

## Verification and device acceptance

Automated tests cover parser examples, source filtering, payload bounds, receiver ordering, stale/removal/disconnect states, meaningful change detection, PNG bounds and circular layout geometry. Both app unit suites, lint and APK builds are checked separately in the build report. No physical phone/watch or emulator was connected for this implementation; actual Maps notification fields, direction readability, vibration, OEM background behavior and Bluetooth timing require device testing.

On the paired devices, check a walking and driving route, a route in the phone's language, phone lock, long roads/instructions and large fonts, distance changes, consecutive turns, route recalculation, arrival/route ending, notification access off/on, navigation off/on, Bluetooth loss/reconnect, opening the watch screen midway through a route and an older watch. Confirm ordinary messages and calls still work.

Android documents the public [notification text fields and navigation category](https://developer.android.com/reference/android/app/Notification) and [notification-listener connection, update and removal callbacks](https://developer.android.com/reference/android/service/notification/NotificationListenerService). Maps controls what it exposes through those fields.
