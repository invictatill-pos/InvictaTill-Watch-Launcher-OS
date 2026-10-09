# Read-only watch heart-rate diagnostic

Use this when HealthSync previously measured heart rate on the same watch but a newer version does not. It records the actual watch and acquisition evidence without reinstalling HealthSync or changing its permissions. Keep the watch on your wrist and open HealthSync's Health screen before starting.

Enable USB debugging yourself and authorize this computer. Run the Android SDK's `platform-tools\adb.exe devices`, then use the watch's exact serial whose state is `device`:

```powershell
.\tools\Collect-Watch-Sensor-Diagnostic.ps1 -Serial 'WATCH_SERIAL' -CaptureSeconds 15
```

When the script prints **tap Measure heart rate now**, tap the measurement button. It waits for the chosen interval, then saves a UTF-8 text report under `tools\diagnostics`. The default wait is 15 seconds; `-CaptureSeconds 60` observes the full acquisition window. No wait can exceed 60 seconds. Use `-CaptureSeconds 0` after an attempted measurement to collect available diagnostics immediately. Each ADB command also has a 20-second timeout.

The report includes:

- Model, manufacturer, hardware, Android/API and build fingerprint read from the selected watch.
- Installed HealthSync version, sensor/motion/location permission facts and HealthSync's AppOps states.
- Static exposed sensor inventory from `dumpsys sensorservice`, and recognized active connections belonging to `com.healthsync.watch`.
- The latest HealthSync collector diagnostic at `files/heart-rate-diagnostic.txt`, read through `run-as` when this build/firmware permits it.
- At most 500 recent sensor diagnostic lines from the observed HealthSync process IDs and the `SensorCollector`, `HRSensor`, `ForegroundPolicy` and `WatchFace` tags.

Global recent sensor readings, fusion states, other applications' sensor connections/registration history and unrelated log lines are omitted from the saved report. A connection can disappear after a reliable sample or acquisition timeout; its absence in a later snapshot does not by itself establish a sensor fault. If firmware uses an unrecognized dump format, raw sensor output is omitted. HealthSync **Settings → Device information → Copy** provides its own Android sensor inventory for that case.

ADB is discovered under `ANDROID_SDK_ROOT`, `ANDROID_HOME` or `%LOCALAPPDATA%\Android\Sdk`. Override it and the report directory when needed:

```powershell
.\tools\Collect-Watch-Sensor-Diagnostic.ps1 -Serial 'WATCH_SERIAL' `
  -AdbPath "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" `
  -ReportDirectory '.\watch-sensor-reports' -CaptureSeconds 60
```

If the earlier working app was **HealthSync**, the recorded package/version identifies the current installation. Also state which earlier HealthSync version worked; this script does not downgrade it. For an explicitly identified different health app, optionally pass its package ID:

```powershell
.\tools\Collect-Watch-Sensor-Diagnostic.ps1 -Serial 'WATCH_SERIAL' `
  -OriginalHealthPackage 'com.example.health'
```

That optional check reads only the named package's version/permission facts, APK path and launcher component. It does not list every installed application, open the other app or read its logs/data. Include its displayed app name yourself because Android package dumps do not reliably resolve localized labels.

Share the generated report with the exact HR status displayed after Measure, and the earlier working version. A successful collection exit code 0 means the report was collected; it does not mean the sensor produced a reading. Exit code 1 means the selected watch/ADB was unavailable or collection stopped. Individual unavailable commands retain their exit codes and error text. Older builds may lack the app-private diagnostic; non-debuggable builds can reject `run-as`.

The script performs no installation, app launch, sensor activation, permission/AppOps changes, data deletion or log clearing. Device state remains under your control.
