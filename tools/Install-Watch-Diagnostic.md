# Watch APK install diagnostic

Connect the watch to this Windows computer, enable USB debugging on the watch, and approve its debugging prompt. Run from the workspace or extracted APK bundle in PowerShell. Supply the watch’s exact ADB serial:

```powershell
.\tools\Install-Watch-Diagnostic.ps1 -Serial 'WATCH_SERIAL'
```

To find the serial, run your Android SDK’s `platform-tools\adb.exe devices`, then choose the line for the watch whose state is `device`. The script requires an explicit serial even when one device is connected.

The default APK is `dist\HealthSync-Watch-2.3.2-Android8.apk`, or `HealthSync-Watch-2.3.2-Android8.apk` in an extracted bundle (beside this script or beside its `tools` folder). When the script is at the bundle root, use `.\Install-Watch-Diagnostic.ps1`. ADB is found under `ANDROID_SDK_ROOT`, `ANDROID_HOME`, or `%LOCALAPPDATA%\Android\Sdk`. Override paths when needed:

```powershell
.\tools\Install-Watch-Diagnostic.ps1 -Serial 'WATCH_SERIAL' `
  -ApkPath '.\dist\HealthSync-Watch-2.3.2-Android8.apk' `
  -AdbPath "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
```

It performs one `adb -s SERIAL install -r --no-streaming APK` attempt and saves a UTF-8 report in `tools\diagnostics`. The report includes the APK size and SHA-256, ADB version, watch model/Android/API/CPU properties, available app storage, package version before/after the attempt, and the exact install stdout, stderr and exit code.

If installation works but the app says “keeps stopping,” add `-CheckStartup`:

```powershell
.\tools\Install-Watch-Diagnostic.ps1 -Serial 'WATCH_SERIAL' -CheckStartup
```

After a successful install, this option opens HealthSync Watch, waits 10 seconds, records the activity launch result and checks whether its process is still running. It reads the crash buffer from the device's startup-check timestamp onward and saves only Java or native crash blocks that explicitly name `com.healthsync.watch`. If the watch shows a permission prompt, grant it while the check runs. A successful check covers this short launch observation; it does not prove every sensor, Bluetooth feature or later activity works.

Exit code 0 means installation succeeded and any requested launch/process check passed. Code 1 means installation failed or diagnostics stopped. Code 2 means installation succeeded but the launch/process check failed or a HealthSync crash was found. The report explicitly states whether crash-buffer verification succeeded; an unavailable timestamp or crash buffer produces a warning and leaves that part unverified.

Share the generated text report, especially its **Exact APK install result** section. Compare its APK size and SHA-256 with the delivered `SHA256SUMS.txt` or build report to confirm the transferred file matches. The generic “App not installed” popup alone does not identify the cause.

The script keeps existing app data during replacement installs. It performs no uninstall, data clearing, global log clearing, security changes, or automatic repair. Without `-CheckStartup`, it reads only the listed device properties, storage totals and HealthSync package state. With `-CheckStartup`, it also checks the HealthSync process and collects filtered HealthSync crash blocks. Other apps' logs are omitted from the saved report.

