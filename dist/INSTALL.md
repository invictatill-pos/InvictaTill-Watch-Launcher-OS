# Install HealthSync 2.9.4

Install `HealthSync-Phone-2.9.4.apk` on the phone. The watch remains on `HealthSync-Watch-2.9.3-Android8.apk`; watches already running 2.9.3 need no update. Phone requires Android 8/API 26; watch requires Android 6/API 23. Both retain the development signing certificate used by earlier deliveries.

Pair in Android Bluetooth settings, open both apps, grant permissions for the features you use, and choose Connect in the phone Device tab. Update both apps to use real sensor provenance consistently.

Bluetooth internet setup is in phone **Device → Internet for watch apps** and watch **Settings → Phone connection → Internet from phone**. Sharing requires Android Bluetooth tethering/PAN support. See [BLUETOOTH-INTERNET.md](BLUETOOTH-INTERNET.md).

This upgrade archives unverifiable historical oxygen/sleep values outside active history. It preserves workouts, step history, and accepted heart-rate records. Missing sensors/measurements remain empty. Rest, stride distance, and calories are labeled estimates; hydration is a manual log; stress/HRV have no generated score.

Phone database versions 4–5 keep verified active history. For undocumented older database versions 1–3, original tables are preserved as local versioned archives and active history starts empty; their meaning and measurement sources are not guessed. Destructive migration fallback is disabled. These database versions differ from the app's displayed release version.

See [ANDROID8-INSTALL.md](ANDROID8-INSTALL.md) for full setup, updates, Home recovery, hashes, and diagnostics. Calls/audio and notification access are covered in [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md).
