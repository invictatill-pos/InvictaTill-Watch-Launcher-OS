# HealthSync communication delivery — Watch 2.4.1 / Phone 2.3.1

Prepared 10 October 2026. Watch version code 17, minimum Android 6.0/API23. Phone version code 11, minimum Android 8.0/API26. Both retain the existing application IDs and development signing configuration for in-place updates.

## Result

This update delivers:
- **Chrono Ultra Luxury Watch Face**: Sleek modern sports chronograph watch face with precision subdials (Heart Rate BPM with zone arc, Step progress arc, Battery gauge), date aperture, luminous markers & skeleton hands, and ultra-low power ambient AOD mode. Replaces the retro Casio digital face.
- **Fixed Phone App First Sync Banner**: Resolved issue where "Connect your watch to get started" was shown even when the watch was already connected.
- **In-App Auto-Update System & R8 Minification Hardening**: Added `@Keep` and Proguard reflection guards ensuring reliable over-the-air update checks directly from GitHub Releases / CDN.
