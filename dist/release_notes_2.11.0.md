# HealthSync 2.11.0 — Readable watch navigation

A dedicated **Navigation** app makes Google Maps directions readable on the watch: large turn instructions, distance to the next maneuver, available direction arrows and trip details in a round-screen layout.

1. Update **both apps to 2.11.0**: phone code **34**, watch code **39**.
2. On the phone, open **Device → Watch navigation → Enable watch navigation** and allow HealthSync **Notification access**. Allow Google Maps notifications too.
3. Start a route in the installed **Google Maps** app, then open **Apps → Navigation** on the watch.

Directions use Maps' route notification over Bluetooth, so no screen-capture approval is needed. They can continue with the phone screen locked while Maps, notification access and the Bluetooth connection remain operational. Trip details and maneuver icons appear when Maps exposes them. Long instructions scroll; Back and Clock remain available.

Disconnected or expired updates hide old guidance. Reconnect restores the current route. Distance updates and heartbeats do not repeatedly buzz. Older watches retain ordinary Maps notifications until updated. The previous **Live phone map** remains an optional screen viewer, separately labeled in the phone app.

Install over the existing apps to retain history; both packages retain the previous signing certificate. Android asks for installation confirmation on each device. Phone requires Android 8/API26 or newer; watch requires Android 6/API23 or newer.

449 unit tests passed. Both builds and signatures verified; lint has zero errors and no added warnings. Actual Maps notification formats, locked-phone delivery, watch readability, vibration and Bluetooth timing still require device testing. HealthSync does not calculate its own route, position, map, lane guidance or ETA.

See **WATCH-NAVIGATION.md**, **ANDROID8-INSTALL.md** and **BUILD-REPORT-Android8-2.11.0.md** in the downloads.
