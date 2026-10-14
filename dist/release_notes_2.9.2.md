# HealthSync 2.9.2 — Passive Maps GPS Fix & Continuous GNSS Satellite Tracking

This update resolves the issue where outdoor workouts stayed stuck on "GPS searching • go outdoors" by enabling passive GPS sharing, removing the 2-minute GNSS reset loop, adding real-time satellite count display, and implementing resilient clock-drift handling for standalone watch GPS.

## Root Cause Fixes

- **Passive Provider Locations Allowed**:
  - Previously, `workoutLocationSourceAllowed()` explicitly blocked `provider != "passive"`. When Maps was running or Android forwarded fixes via `LocationManager.PASSIVE_PROVIDER`, HealthSync discarded every fix. Passive updates with accuracy <= 60m are now fully accepted as GPS fixes.

- **Eliminated 2-Minute Baseband GNSS Reset Loop**:
  - `shouldRecoverWorkoutLocation()` previously tore down and recreated the location listeners every 120 seconds if a fix hadn't yet been acquired. On standalone watches without cellular A-GPS, cold satellite ephemeris download requires 2-4 minutes of uninterrupted listening. The recovery threshold is now 15 minutes, allowing continuous GNSS tracking.

- **Real-Time GNSS Satellite Acquisition Display**:
  - Registered `GnssStatus.Callback` to continuously track visible and used satellite counts directly from the hardware GNSS baseband.
  - While searching outdoors, the UI now displays live satellite visibility: `"GPS acquiring (X sats) • wait for lock • step estimate"`. As soon as a fix is acquired, it locks instantly with accuracy: `"GPS locked ±Xm • 1 pts"`.

- **Pre-Session Origin Fix Adoption**:
  - HealthSync now accepts fixes captured up to 2 minutes prior to session start (`allowRecentOrigin = isInitialFix`). If the user checks Google Maps or steps outside right before tapping "Start Walk", HealthSync immediately adopts that location as the starting origin.

- **Resilient Satellite Clock Timing & Skew Tolerance**:
  - Driver nano jitter and forward clock skew are clamped to `nowElapsed`, preventing negative elapsed time from rejecting live callbacks.
  - Forward skew tolerance extended to 60 seconds with fallback to `nowElapsed` for live callbacks delivered by the OS, ensuring watches with unsynchronized system clocks never drop real satellite fixes.

- **A-GPS Injection**:
  - Sends `force_time_injection` and `force_xtra_injection` via `sendExtraCommand` on location initialization to accelerate satellite acquisition.

## Update Installation

- **Watch**: Install `HealthSync-Watch.apk` (2.9.2, code 36).
- **Phone**: Install `HealthSync-Phone.apk` (2.9.2, code 30).
- Existing users can update directly via the in-app update dialog or by installing the APKs from this release.
