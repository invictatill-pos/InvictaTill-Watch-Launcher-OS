[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string]$Serial,

    [string]$AdbPath,
    [string]$ReportDirectory = (Join-Path $PSScriptRoot 'diagnostics'),
    [ValidateRange(0, 60)]
    [int]$CaptureSeconds = 15,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$')]
    [string]$OriginalHealthPackage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:sensorReportPath = $null
$script:sensorAdbPath = $null
$script:healthPackage = 'com.healthsync.watch'

function Write-SensorReport {
    param([string]$Text)
    if ($script:sensorReportPath) {
        [IO.File]::AppendAllText($script:sensorReportPath, $Text + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
    }
}

function ConvertTo-SensorNativeArgument {
    param([string]$Argument)
    $quoted = [regex]::Replace($Argument, '(\\*)"', '$1$1\"')
    $quoted = [regex]::Replace($quoted, '(\\+)$', '$1$1')
    return '"' + $quoted + '"'
}

function Invoke-SensorAdb {
    param([string]$Label, [string[]]$Arguments, [scriptblock]$Transform, [int]$TimeoutMs = 20000)
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:sensorAdbPath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.PSObject.Properties.Name -contains 'ArgumentList') {
        foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
    } else {
        $startInfo.Arguments = ($Arguments | ForEach-Object { ConvertTo-SensorNativeArgument $_ }) -join ' '
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMs)) {
            $process.Kill()
            [void]$process.WaitForExit(5000)
            $exitCode = 124
            $timeoutNote = "Command exceeded $TimeoutMs ms and was stopped."
        } else {
            $exitCode = $process.ExitCode
            $timeoutNote = ''
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
    } finally {
        $process.Dispose()
    }
    Write-SensorReport "`n[$Label]"
    Write-SensorReport ('Arguments: ' + (($Arguments | ForEach-Object { ConvertTo-SensorNativeArgument $_ }) -join ' '))
    Write-SensorReport "Exit code: $exitCode"
    $selectedOutput = if ($Transform) { & $Transform $stdout } else { $stdout }
    if ([string]::IsNullOrWhiteSpace($selectedOutput)) { $selectedOutput = '(No output.)' }
    Write-SensorReport ('STDOUT:' + [Environment]::NewLine + $selectedOutput.TrimEnd())
    Write-SensorReport ('STDERR:' + [Environment]::NewLine + $stderr.TrimEnd())
    if ($timeoutNote) { Write-SensorReport $timeoutNote }
    return [pscustomobject]@{ ExitCode = $exitCode; Stdout = $stdout; Stderr = $stderr }
}

function Select-SensorPackageFacts {
    param([string]$Text)
    $pattern = 'versionCode=|versionName=|codePath=|targetSdk=|minSdk=|pkgFlags=|privateFlags=|^\s*User \d+:|requested permissions:|install permissions:|runtime permissions:|android\.permission\.(BODY_SENSORS|ACTIVITY_RECOGNITION|HIGH_SAMPLING_RATE_SENSORS|FOREGROUND_SERVICE|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION)|Unable to find package|was not found|Unknown package'
    $facts = @($Text -split '\r?\n' | Where-Object { $_ -match $pattern })
    if ($facts.Count -eq 0) { return '(No matching package/version/health-permission facts reported.)' }
    return $facts -join [Environment]::NewLine
}

function Select-SensorInventory {
    param([string]$Text)
    # Only the static Sensor List section is retained. Fusion states, recent
    # readings, global active sensors and registration history are excluded.
    $inventory = [Collections.Generic.List[string]]::new()
    $inside = $false
    $bounded = $false
    foreach ($line in ($Text -split '\r?\n')) {
        if ($line -match '^\s*Sensor List:\s*$') { $inside = $true; $inventory.Add($line); continue }
        if ($inside -and $line -match '^\s*(Fusion States|Recent Sensor events|Active sensors|Active connections|Sensor Privacy|Sensor Service|Socket Buffer|WakeLock|Mode|Previous Registrations|Sensor Registrations|Connection Number)\b') { $bounded = $true; break }
        if ($inside) { $inventory.Add($line) }
    }
    if ($inventory.Count -eq 0 -or -not $bounded) {
        return '(Static Sensor List section unavailable or unrecognized. No raw sensor dump saved. Use HealthSync Settings > Device information > Copy to obtain the Android sensor inventory.)'
    }
    return ($inventory | Select-Object -First 1000) -join [Environment]::NewLine
}

function Select-HealthSensorConnections {
    param([string]$Text)
    # AOSP connection dumps use an unindented Connection Number header followed
    # by indented package/sensor lines. Keep only blocks naming HealthSync exactly.
    $blocks = [regex]::Matches($Text, '(?m)^[ \t]*Connection Number:[^\r\n]*\r?\n(?:(?![ \t]*Connection Number:)(?:[ \t]+[^\r\n]*|[ \t]*)\r?\n)*')
    $packagePattern = '^\s*(?:Package(?:Name)?\s*[:=]\s*)?' + [regex]::Escape($script:healthPackage) + '(?:\s+\||\s*$)'
    $selected = [Collections.Generic.List[string]]::new()
    foreach ($block in $blocks) {
        if (@($block.Value -split '\r?\n' | Where-Object { $_ -match $packagePattern }).Count -gt 0) {
            $selected.Add($block.Value.TrimEnd())
        }
    }
    if ($selected.Count -eq 0) {
        return '(No recognized active HealthSync sensor connection. An acquisition may have ended, the app may not be running, or this firmware may use a different dump format. Other applications'' connections and readings are omitted.)'
    }
    return $selected -join ([Environment]::NewLine + [Environment]::NewLine)
}

function Select-HealthSensorLogs {
    param([string]$Text, [string[]]$ProcessIds, [int]$MaxLines = 500)
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in ($Text -split '\r?\n')) {
        if ($line -match '^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(?<sensorPid>\d+)\s+\d+\s+[VDIWEF]\s+(?<sensorTag>SensorCollector|HRSensor|ForegroundPolicy|WatchFace)\s*:' -and
            $ProcessIds -contains $Matches['sensorPid']) { $lines.Add($line) }
    }
    if ($lines.Count -eq 0) { return '(No matching HealthSync sensor-tag lines for its observed process IDs. Read the app-private collector diagnostic and measurement status as well.)' }
    return ($lines | Select-Object -Last $MaxLines) -join [Environment]::NewLine
}

try {
    if ([string]::IsNullOrWhiteSpace($AdbPath)) {
        $sdkDirectories = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
        if ($env:LOCALAPPDATA) { $sdkDirectories += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
        $AdbPath = $sdkDirectories | Where-Object { $_ } |
            ForEach-Object { Join-Path $_ 'platform-tools\adb.exe' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        if (-not $AdbPath) { throw 'ADB not found. Pass -AdbPath with the Android SDK platform-tools\adb.exe path.' }
    }
    $script:sensorAdbPath = (Resolve-Path -LiteralPath $AdbPath).ProviderPath
    [void](New-Item -ItemType Directory -Path $ReportDirectory -Force)
    $resolvedReportDirectory = (Resolve-Path -LiteralPath $ReportDirectory).ProviderPath
    $script:sensorReportPath = Join-Path $resolvedReportDirectory ('Watch-sensors-{0}-{1}.txt' -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID)
    Write-SensorReport 'HealthSync Watch read-only sensor diagnostic'
    Write-SensorReport ('Started: ' + [DateTimeOffset]::Now.ToString('o'))
    Write-SensorReport "Explicit device serial: $Serial"
    Write-SensorReport "Capture wait: $CaptureSeconds seconds"
    Write-SensorReport 'No install, launch, data clearing, permission changes, sensor activation or log clearing is performed.'
    Write-Host "Diagnostic report: $script:sensorReportPath"
    $version = Invoke-SensorAdb 'ADB version' @('version')
    if ($version.ExitCode -ne 0 -or $version.Stdout -notmatch 'Android Debug Bridge') { throw 'The selected executable did not report a working Android Debug Bridge.' }
    $state = Invoke-SensorAdb 'Target device state' @('-s', $Serial, 'get-state')
    if ($state.ExitCode -ne 0 -or $state.Stdout.Trim() -ne 'device') { throw 'The explicit watch is unavailable, offline or unauthorized. No device state was changed.' }

    foreach ($property in @('ro.product.manufacturer', 'ro.product.model', 'ro.product.device', 'ro.product.board', 'ro.hardware', 'ro.build.fingerprint', 'ro.build.version.release', 'ro.build.version.sdk')) {
        [void](Invoke-SensorAdb "Device property: $property" @('-s', $Serial, 'shell', 'getprop', $property))
    }
    [void](Invoke-SensorAdb 'HealthSync version and permission facts' @('-s', $Serial, 'shell', 'dumpsys', 'package', $script:healthPackage) { param($text) Select-SensorPackageFacts $text })
    [void](Invoke-SensorAdb 'HealthSync AppOps' @('-s', $Serial, 'shell', 'cmd', 'appops', 'get', $script:healthPackage))
    if ($OriginalHealthPackage) {
        [void](Invoke-SensorAdb 'Explicit earlier health-app package facts' @('-s', $Serial, 'shell', 'dumpsys', 'package', $OriginalHealthPackage) { param($text) Select-SensorPackageFacts $text })
        [void](Invoke-SensorAdb 'Explicit earlier health-app APK path' @('-s', $Serial, 'shell', 'pm', 'path', $OriginalHealthPackage))
        [void](Invoke-SensorAdb 'Explicit earlier health-app launcher component' @('-s', $Serial, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-p', $OriginalHealthPackage))
    }
    $before = Invoke-SensorAdb 'HealthSync process before capture' @('-s', $Serial, 'shell', 'pidof', $script:healthPackage)
    Write-Host "Open HealthSync Health and tap Measure heart rate now. Keep the watch on your wrist. Capture lasts $CaptureSeconds seconds."
    Write-SensorReport 'User action: manually tap Measure heart rate in HealthSync. The script does not send a measurement command.'
    if ($CaptureSeconds -gt 0) { Start-Sleep -Seconds $CaptureSeconds }
    $after = Invoke-SensorAdb 'HealthSync process after capture' @('-s', $Serial, 'shell', 'pidof', $script:healthPackage)
    $processIds = @(($before.Stdout + ' ' + $after.Stdout) -split '\s+' | Where-Object { $_ -match '^\d+$' } | Select-Object -Unique)
    $sensorDump = Invoke-SensorAdb 'Static Android sensor inventory' @('-s', $Serial, 'shell', 'dumpsys', 'sensorservice') { param($text) Select-SensorInventory $text }
    Write-SensorReport "`n[HealthSync-only active sensor connections]"
    Write-SensorReport (Select-HealthSensorConnections $sensorDump.Stdout)
    [void](Invoke-SensorAdb 'HealthSync collector diagnostic file' @('-s', $Serial, 'shell', 'run-as', $script:healthPackage, 'cat', 'files/heart-rate-diagnostic.txt') {
        param($text)
        if ($text.Length -gt 32768) { return $text.Substring(0, 32768) + "`n(App-private diagnostic truncated at 32 KB.)" }
        return $text
    })
    if ($processIds.Count -gt 0) {
        # Known tags keep unrelated log volume low; the final transform also
        # verifies PID ownership and retains at most 500 matching lines.
        $linesPerProcess = [Math]::Max(1, [Math]::Floor(500 / $processIds.Count))
        foreach ($healthProcessId in $processIds) {
            $logs = Invoke-SensorAdb "HealthSync-only collector logs for PID $healthProcessId" @('-s', $Serial, 'logcat', '-d', '-v', 'threadtime', '-t', '500', '--pid', $healthProcessId, 'SensorCollector:D', 'HRSensor:D', 'ForegroundPolicy:W', 'WatchFace:W', '*:S') {
                param($text) Select-HealthSensorLogs $text $processIds $linesPerProcess
            }
            if ($logs.ExitCode -ne 0 -and ($logs.Stderr + $logs.Stdout) -match '(?i)unknown option|unrecognized option|invalid option') {
                [void](Invoke-SensorAdb 'Compatibility log read with saved-output PID filtering' @('-s', $Serial, 'logcat', '-d', '-v', 'threadtime', '-t', '500', 'SensorCollector:D', 'HRSensor:D', 'ForegroundPolicy:W', 'WatchFace:W', '*:S') {
                    param($text) Select-HealthSensorLogs $text $processIds $linesPerProcess
                })
                break
            }
        }
    } else {
        Write-SensorReport "`n[HealthSync-only recent collector logs]`nNot collected: no HealthSync process ID was found. Open HealthSync and rerun."
    }
    Write-SensorReport ('Completed: ' + [DateTimeOffset]::Now.ToString('o'))
    Write-SensorReport 'Collection completed. Missing permissions, unsupported dumps/run-as, rejected frames or no readings remain diagnostic findings, not a successful sensor test.'
    Write-Host "Saved sensor diagnostic: $script:sensorReportPath"
    Write-Host 'Include the HR status shown on the watch and whether earlier HealthSync measured on this same watch.'
    exit 0
} catch {
    Write-SensorReport ('Diagnostic stopped: ' + $_.Exception.Message)
    Write-Error -Message $_.Exception.Message -ErrorAction Continue
    if ($script:sensorReportPath) { Write-Host "Report: $script:sensorReportPath" }
    exit 1
}
