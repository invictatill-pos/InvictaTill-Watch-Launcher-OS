# Install HealthSync (Phone 2.0.0 / Watch 2.0.1)

1. Transfer HealthSync-Phone-2.0.0.apk to your Android phone and install it.
2. Transfer HealthSync-Watch-2.0.1-Android8.apk to your Android watch's local storage and install it.
3. Allow installation from the file manager/source you use if Android asks.
4. Pair the watch and phone in Android Bluetooth settings.
5. Open HealthSync on both devices and grant the explained permissions.
6. On the phone, enable HealthSync Notification Access and use the Device tab to connect.
7. In Settings, set your step goal and weight/height, then Apply to Watch. Saved settings also resend on reconnection.

The phone app needs Android 8.0 or newer; the watch app needs Android 6.0 or newer and Bluetooth Classic RFCOMM support. Both APKs use this computer's existing Android development signing certificate. The phone is an optimized release build; the watch uses the same unoptimized debug format as the working app-debug-37.apk supplied for comparison.

For Android 8.0 watches showing "App not installed", see ANDROID8-INSTALL.md. The Watch 2.0.1 APK has explicit v1/v2 signing and treats location hardware as optional; the Phone 2.0.0 APK works with this watch version.

Install over the previous apps to retain local data when their signing certificate matches. Do not uninstall the previous apps to work around a signature mismatch if you need their stored history.

Workout calories and stride-derived distances are estimates. Unsupported sensors show unavailable. Replies require a source notification with a reply action. Bluetooth, vendor sensors, GPS and call controls still need verification on your actual devices.

Manual phone Disconnect remains paused until you choose Connect again. Workout sessions interrupted by a process restart recover paused; choose Resume to continue. Closed five-minute step buckets synchronize automatically, so chart buckets can trail live steps by a few minutes.
