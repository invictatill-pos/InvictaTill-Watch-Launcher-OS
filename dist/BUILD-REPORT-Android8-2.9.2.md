# HealthSync 2.9.2 — Build Report

Prepared 14 October 2026. Watch version code **36**; phone version code **30**.

## Delivered Changes

- Enabled passive location provider updates (`LocationManager.PASSIVE_PROVIDER`) and fused provider sharing in `WorkoutLocationRules` and `WorkoutTrackingService`.
- Extended cold GNSS recovery window from 120s to 900s (15 min) in `shouldRecoverWorkoutLocation` to prevent tearing down the hardware GNSS listener mid-search.
- Added live hardware satellite tracking via `GnssStatus.Callback`, reporting visible/used satellite counts during acquisition.
- Updated `ActiveWorkoutActivity` to display live satellite count: `"GPS acquiring (X sats) • wait for lock"`.
- Permitted adoption of pre-session location fixes up to 120s old (`allowRecentOrigin = isInitialFix`).
- Clamped driver nanos clock advance and extended skew buffer in `workoutFixElapsedTime`.
- Added `ACCESS_LOCATION_EXTRA_COMMANDS` permission and triggered time/xtra injection.
- Updated versionCode and versionName to 2.9.2 across watch and phone apps.

## Validation

| Check | Result |
| :--- | :--- |
| Phone unit tests | 124 passed, 0 failures/errors |
| Watch unit tests | 231 passed, 0 failures/errors |
| Phone optimized release APK | Built successfully |
| Watch debug APK | Built successfully |
| APK signatures | Verified; watch retains v1/v2, phone v2 |
| Upgrade certificate | Verified matching development certificate |
| SHA-256 manifests | Generated via `tools/update_dist.ps1` |
