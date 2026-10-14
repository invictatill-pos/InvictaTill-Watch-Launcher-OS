# HealthSync 2.9.1 — Build Report

Prepared 14 October 2026. Watch version code **35**; phone version code **29**.

## Delivered Changes

- Relaxed workout location accuracy cutoff from 50m to 120m (`MAX_WORKOUT_ACCURACY_METERS`) for smartwatch GNSS hardware.
- Added 15-second clock-skew tolerance in `workoutFixElapsedTime` preventing rejection of satellite timestamps when offline.
- Added Android `Criteria.ACCURACY_FINE` + `POWER_HIGH` and API 31+ `LocationRequest.QUALITY_HIGH_ACCURACY` requests in `WorkoutTrackingService`.
- Graceful default accuracy fallback when hardware omits `hasAccuracy()` metadata.
- Capped movement threshold at 12m for walking pace detection in `workoutRouteSegmentDecision`.
- Maintained strict 30-second stale fix timeout and mock location rejection.
- Phone companion bump to 2.9.1 (versionCode 29) for protocol alignment.

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
