# U8 Ultra sensor and installed-app fallback — 2.7.1

HealthSync tries a real Android/vendor metric sensor first. If it cannot register or produce an accepted capture, it checks readings shared by installed watch health apps. Heart-rate acquisition lasts up to 60 seconds, oxygen up to 30 seconds, followed by a bounded export check. Plausible accuracy-zero sensor values appear as **Unverified sensor** feedback; they do not enter health history or workout statistics.

On the Kolabee U8 Ultra, open **Health readings → Open watch health app**, select **Heart rate** or **Blood oxygen**, and measure while wearing the watch. Return to HealthSync and choose **Check watch heart rate data** or **Check watch oxygen data**. The oxygen screen and heart-rate details offer the same controls. Imports show the supplying app and original capture age. They synchronize to the updated phone app. Both devices must run 2.7.1 to store app provenance.

The apps' display names do not establish their package IDs or export schemas. No physical device was attached during implementation. If the original apps keep records private, Android cannot supply them to HealthSync. The UI explains this, and the original apps remain available for measuring. A device-specific adapter requires the actual package/provider or vendor SDK contract; HealthSync never invents missing values.

## Export compatibility

Only exported, enabled providers with readable permissions are queried. Suggested health apps are checked at named metric paths (`heart_rate`, `heart_rate_bpm`, `heartrate`, `spo2`, `oxygen_saturation`, `spo2_percent`). A typed schema requires an explicit BPM/saturation column and an original epoch capture timestamp. Generic `value` columns, row IDs, elapsed uptime, raw PPG, and unsupported ranges are rejected. Reads are limited by time, provider/query count, and row count. A provider that ignores cancellation cannot create unlimited queued work.

Providers can declare a schema with string metadata prefixed `com.healthsync.export.heart_rate.` or `com.healthsync.export.spo2.`:

| Metadata suffix | Example | Meaning |
| --- | --- | --- |
| `uri` | `content://com.vendor.health/heart_rate` | A non-root URI owned by the exported provider |
| `value_column` | `bpm` | Column containing the metric value |
| `timestamp_column` | `captured_at_ms` | Original capture timestamp |
| `unit` | `bpm` / `percent` | Explicit metric unit |
| `timestamp_unit` | `epoch_ms` / `epoch_seconds` | Explicit wall-clock time unit |

App data is stored as `WATCH_HEALTH_APP` with package, label, and original timestamp. Imported HR accuracy zero means unavailable sensor-accuracy metadata. Display imports expire after 24 hours. Workout samples must be captured after workout start/resume and be at most two minutes old. Repeated exports are deduplicated, and older imports cannot replace newer direct sensor captures.

## Workout location

Enable Android Location and grant HealthSync precise location access. Start an outdoor workout. Tracking requests GPS/network/OEM fixes, listens to Android passive fixes originating from other watch apps, and checks fresh caches captured since the workout/resume. Shared location updates still require another app or the system to produce a fix. Retry restarts the subscriptions; a stalled subscription is retried after two minutes.

GPS hardware or reception cannot be created in software. Weak accuracy, no provider, disabled location, denied permission, and stale fixes are reported separately. Routes reject mock/stale/reordered/poor fixes and keep gaps separate. Walking/running step distance remains labeled as an estimate.

## Device acceptance

1. Update both APKs without uninstalling; confirm existing history remains.
2. Allow sensors, measure HR/oxygen, then repeat off wrist. Confirm capture ages and **Unverified sensor** labels where firmware reports accuracy zero.
3. Measure in each original watch app, then retry **Check watch app data**. Confirm the app label and original capture time or the explicit unsupported-sharing message.
4. Repeat export checks without a new capture; ensure no duplicate phone history appears. A saved pre-workout HR reading must not create workout samples.
5. Deny sensor permission and retry an app export. Its own provider permissions still apply.
6. Test outdoors with precise location on, off, denied, and weak reception. Test Retry, pause/resume, and a second watch app requesting location. Reject cached pre-workout/mock locations.
7. Copy **Heart rate details → Copy heart rate report** if capture still fails. Device information includes the exposed sensor inventory; the installed apps' package/export details are needed for an OEM adapter.
