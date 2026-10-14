# HealthSync 2.9.1 — High-Level Workout Walk GPS & Stability Update

This update introduces high-level GPS tracking enhancements tailored specifically for smartwatch GNSS antennas, offline clock-drift tolerance, and high-priority Android location requests.

## Workout GPS Improvements

- **Adaptive Smartwatch Accuracy Ceiling (120m)**:
  - Smartwatch wrist antennas often report outdoor initial satellite fixes between 55m and 110m.
  - Previous releases strictly rejected fixes above 50m; HealthSync now accepts fixes up to 120m with distance smoothing, ensuring outdoor walks and runs capture full GPS routes without getting stuck in searching.

- **Offline Clock-Skew Protection**:
  - Adds a 15-second forward clock-drift buffer for offline watches. If the watch's internal RTC drifts slightly behind atomic GPS satellite time, fixes are safely aligned to current uptime instead of discarded.

- **High-Priority Hardware GNSS Requests**:
  - Requests Android `Criteria.ACCURACY_FINE` + `POWER_HIGH` and Android 12+ `LocationRequest.QUALITY_HIGH_ACCURACY` with 500ms intervals to keep the satellite receiver active at peak sensitivity.
  - Gracefully handles firmware that omits `hasAccuracy()` metadata.

- **Walking Stride Segmentation**:
  - Capped movement threshold at 12m so standard walking paces (~1.2 m/s) reliably register as movement and accumulate GPS distance.

## Companion Phone & Studio Compatibility

- Synchronized companion phone build with updated protocol handling for GPS route points.
- Refined Face Studio stability and watch face transfer pipeline.

## Update Installation

- **Watch**: Install `HealthSync-Watch.apk` (2.9.1, code 35).
- **Phone**: Install `HealthSync-Phone.apk` (2.9.1, code 29).
- Existing users can update directly via in-app update controls or by downloading the APKs from this release.
