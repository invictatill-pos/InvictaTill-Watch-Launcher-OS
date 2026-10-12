# Install HealthSync 2.7.0

Install `HealthSync-Phone-2.7.0.apk` on the phone and `HealthSync-Watch-2.7.0-Android8.apk` on the watch as updates over the existing apps. Phone requires Android 8/API 26; watch requires Android 6/API 23. Both retain the development signing certificate used by earlier deliveries.

Pair in Android Bluetooth settings, open both apps, grant permissions for the features you use, and choose Connect in the phone Device tab. Update both apps to use real sensor provenance consistently.

This upgrade archives unverifiable historical oxygen/sleep values outside active history. It preserves workouts, step history, and accepted heart-rate records. Missing sensors/measurements remain empty. Rest, stride distance, and calories are labeled estimates; hydration is a manual log; stress/HRV have no generated score.

Phone database versions 4–5 keep verified active history. For undocumented older database versions 1–3, original tables are preserved as local versioned archives and active history starts empty; their meaning and measurement sources are not guessed. Destructive migration fallback is disabled. These database versions differ from the app's displayed release version.

See [ANDROID8-INSTALL.md](ANDROID8-INSTALL.md) for full setup, updates, Home recovery, hashes, and diagnostics. Calls/audio and notification access are covered in [COMMUNICATION-SETUP.md](COMMUNICATION-SETUP.md).
