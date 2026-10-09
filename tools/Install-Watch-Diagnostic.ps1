[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [ValidatePattern('^[^\s]+$')]
    [string]$Serial,

    [string]$ApkPath,
    [string]$AdbPath,
    [string]$ReportDirectory = (Join-Path $PSScriptRoot 'diagnostics'),
    [switch]$CheckStartup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:diagnosticReportPath = $null
$script:diagnosticAdbPath = $null

function Write-DiagnosticText {
    param([string]$Text)
    if ($script:diagnosticReportPath) {
        [System.IO.File]::AppendAllText(
            $script:diagnosticReportPath,
            $Text + [Environment]::NewLine,
            [System.Text.UTF8Encoding]::new($false)
        )
    }
}

function ConvertTo-NativeArgument {
    param([string]$Argument)
    # Windows CommandLineToArgvW escaping for Windows PowerShell 5.1.
    $quoted = [regex]::Replace($Argument, '(\\*)"', '$1$1\"')
    $quoted = [regex]::Replace($quoted, '(\\+)$', '$1$1')
    return '"' + $quoted + '"'
}

function Invoke-DiagnosticAdb {
    param(
        [string]$Label,
        [string[]]$Arguments,
        [string]$OutputPattern,
        [scriptblock]$OutputTransform
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:diagnosticAdbPath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.PSObject.Properties.Name -contains 'ArgumentList') {
        foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
    } else {
        $startInfo.Arguments = ($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        # Drain both pipes concurrently, including an install rejection on stderr.
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $exitCode = $process.ExitCode
    } finally {
        $process.Dispose()
    }

    Write-DiagnosticText "`n[$Label]"
    Write-DiagnosticText ('Arguments: ' + (($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '))
    Write-DiagnosticText "Exit code: $exitCode"
    if ($OutputTransform) {
        $stdoutForReport = & $OutputTransform $stdout
    } elseif ($OutputPattern) {
        # Package dumps can be large; retain only version/installation state.
        $stdoutForReport = (($stdout -split '\r?\n') | Where-Object { $_ -match $OutputPattern }) -join [Environment]::NewLine
        if ([string]::IsNullOrWhiteSpace($stdoutForReport)) { $stdoutForReport = '(No package version or installed state reported.)' }
    } else {
        $stdoutForReport = $stdout
    }
    Write-DiagnosticText ('STDOUT:' + [Environment]::NewLine + $stdoutForReport.TrimEnd())
    Write-DiagnosticText ('STDERR:' + [Environment]::NewLine + $stderr.TrimEnd())
    return [pscustomobject]@{ ExitCode = $exitCode; Stdout = $stdout; Stderr = $stderr }
}

function Select-HealthSyncCrash {
    param([string]$LogcatText)
    # Only retain crash-buffer blocks that explicitly name this package. Other
    # applications' lines are never written to the report, even when interleaved.
    $blocks = @{}
    $selected = @{}
    $blockSequence = @{}
    foreach ($line in ($LogcatText -split '\r?\n')) {
        if ($line -notmatch '^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(?<pid>\d+)\s+\d+\s+[VDIWEF]\s+(?<tag>AndroidRuntime|DEBUG)\s*:\s*(?<message>.*)$') { continue }
        $crashPid = $Matches['pid']
        $crashTag = $Matches['tag']
        $crashMessage = $Matches['message']
        $key = "$crashTag`:$crashPid"
        if (-not $blockSequence.ContainsKey($key)) { $blockSequence[$key] = 0 }
        if (($crashTag -eq 'DEBUG' -and $crashMessage -match '^\*\*\* \*\*\*') -or
            ($crashTag -eq 'AndroidRuntime' -and $crashMessage -match '^FATAL EXCEPTION:')) { $blockSequence[$key]++ }
        $key += ':' + $blockSequence["$crashTag`:$crashPid"]
        if (-not $blocks.ContainsKey($key)) { $blocks[$key] = [System.Collections.Generic.List[string]]::new() }
        $blocks[$key].Add($line)
        if ($crashMessage -match '^Process:\s+com\.healthsync\.watch\s*,\s*PID:\s*\d+\s*$' -or
            $crashMessage -match '>>>\s+com\.healthsync\.watch\s+<<<') { $selected[$key] = $true }
    }
    $matchingBlocks = @($blocks.Keys | Where-Object { $selected.ContainsKey($_) } | Sort-Object)
    if ($matchingBlocks.Count -eq 0) { return '(No HealthSync Watch crash found in the selected crash-buffer interval.)' }
    return ($matchingBlocks | ForEach-Object { $blocks[$_] -join [Environment]::NewLine }) -join ([Environment]::NewLine + [Environment]::NewLine)
}

try {
    $packageName = 'com.healthsync.watch'
    $workspaceDirectory = Split-Path -Parent $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        $apkCandidates = @(
            (Join-Path $workspaceDirectory 'dist\HealthSync-Watch-2.3.2-Android8.apk'),
            (Join-Path $workspaceDirectory 'HealthSync-Watch-2.3.2-Android8.apk'),
            (Join-Path $PSScriptRoot 'HealthSync-Watch-2.3.2-Android8.apk')
        )
        $ApkPath = $apkCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        if (-not $ApkPath) { throw 'Watch APK not found. Pass -ApkPath with the full path to HealthSync-Watch-2.3.2-Android8.apk.' }
    }
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).ProviderPath
    if ([System.IO.Path]::GetExtension($resolvedApk) -ne '.apk') { throw 'ApkPath must point to an APK file.' }

    if ([string]::IsNullOrWhiteSpace($AdbPath)) {
        $sdkDirectories = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
        if ($env:LOCALAPPDATA) { $sdkDirectories += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
        $AdbPath = $sdkDirectories | Where-Object { $_ } |
            ForEach-Object { Join-Path $_ 'platform-tools\adb.exe' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        if (-not $AdbPath) { throw 'ADB not found in the Android SDK. Pass -AdbPath with the full path to platform-tools\adb.exe.' }
    }
    $script:diagnosticAdbPath = (Resolve-Path -LiteralPath $AdbPath).ProviderPath
    if (-not (Test-Path -LiteralPath $script:diagnosticAdbPath -PathType Leaf)) { throw 'AdbPath must point to a file.' }

    [void](New-Item -ItemType Directory -Path $ReportDirectory -Force)
    $resolvedReportDirectory = (Resolve-Path -LiteralPath $ReportDirectory).ProviderPath
    $reportName = 'Watch-install-{0}-{1}.txt' -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID
    $script:diagnosticReportPath = Join-Path $resolvedReportDirectory $reportName
    $apkFile = Get-Item -LiteralPath $resolvedApk
    $apkHash = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash
    Write-DiagnosticText 'HealthSync Watch installation diagnostic'
    Write-DiagnosticText ('Started: ' + [DateTimeOffset]::Now.ToString('o'))
    Write-DiagnosticText "Explicit device serial: $Serial"
    Write-DiagnosticText "APK: $resolvedApk"
    Write-DiagnosticText "APK bytes: $($apkFile.Length)"
    Write-DiagnosticText "APK SHA256: $apkHash"
    Write-DiagnosticText "ADB: $script:diagnosticAdbPath"
    Write-Host "Diagnostic report: $script:diagnosticReportPath"

    $version = Invoke-DiagnosticAdb -Label 'ADB version' -Arguments @('version')
    if ($version.ExitCode -ne 0 -or $version.Stdout -notmatch 'Android Debug Bridge') { throw 'The selected executable did not report a working Android Debug Bridge.' }
    $state = Invoke-DiagnosticAdb -Label 'Target device state' -Arguments @('-s', $Serial, 'get-state')
    if ($state.ExitCode -ne 0 -or $state.Stdout.Trim() -ne 'device') { throw 'The specified device is unavailable, offline, or has not authorized USB debugging. See the exact ADB response in the report.' }

    foreach ($property in @('ro.product.manufacturer', 'ro.product.model', 'ro.product.device', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.product.cpu.abilist', 'ro.product.cpu.abi')) {
        [void](Invoke-DiagnosticAdb -Label "Device property: $property" -Arguments @('-s', $Serial, 'shell', 'getprop', $property))
    }
    [void](Invoke-DiagnosticAdb -Label 'Available /data storage (KB)' -Arguments @('-s', $Serial, 'shell', 'df', '-k', '/data'))
    $packagePattern = 'versionCode=|versionName=|^\s*User \d+:.*installed=|Unable to find package|was not found|Unknown package'
    [void](Invoke-DiagnosticAdb -Label 'Watch package before install' -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'package', $packageName) -OutputPattern $packagePattern)

    Write-Host 'Installing the selected watch APK on the explicit device…'
    $install = Invoke-DiagnosticAdb -Label 'Exact APK install result' -Arguments @('-s', $Serial, 'install', '-r', '--no-streaming', $resolvedApk)
    if ($install.Stdout) { Write-Host $install.Stdout.TrimEnd() }
    if ($install.Stderr) { Write-Host $install.Stderr.TrimEnd() }
    [void](Invoke-DiagnosticAdb -Label 'Watch package after install' -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'package', $packageName) -OutputPattern $packagePattern)
    $success = $install.ExitCode -eq 0 -and $install.Stdout -match '(?m)^\s*Success\s*$'
    $startupFailed = $false
    if ($success -and $CheckStartup) {
        Write-Host 'Checking HealthSync Watch launch and observing it for 10 seconds…'
        $deviceTime = Invoke-DiagnosticAdb -Label 'Device startup-check timestamp' -Arguments @('-s', $Serial, 'shell', "date '+%m-%d %H:%M:%S.000'")
        $startupTimestamp = $deviceTime.Stdout.Trim()
        $validTimestamp = $deviceTime.ExitCode -eq 0 -and $startupTimestamp -match '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$'
        $launch = Invoke-DiagnosticAdb -Label 'HealthSync Watch activity launch' -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', "$packageName/.ui.WatchFaceActivity")
        Start-Sleep -Seconds 10
        $running = Invoke-DiagnosticAdb -Label 'HealthSync Watch process after 10 seconds' -Arguments @('-s', $Serial, 'shell', 'pidof', $packageName)
        $launchOk = $launch.ExitCode -eq 0 -and $launch.Stdout -match '(?m)^\s*Status:\s*ok\s*$'
        $processAlive = $running.ExitCode -eq 0 -and $running.Stdout.Trim() -match '^\d+(\s+\d+)*$'
        $appCrash = $false
        $crashReadOk = $false
        if ($validTimestamp) {
            $crashes = Invoke-DiagnosticAdb -Label 'HealthSync-only startup crashes' -Arguments @('-s', $Serial, 'logcat', '-b', 'crash', '-d', '-v', 'threadtime', '-T', $startupTimestamp) -OutputTransform { param($logText) Select-HealthSyncCrash $logText }
            $crashReadOk = $crashes.ExitCode -eq 0
            $filteredCrashes = Select-HealthSyncCrash $crashes.Stdout
            $appCrash = $filteredCrashes -notlike '(No HealthSync Watch crash found*'
        } else {
            Write-DiagnosticText '[HealthSync-only startup crashes]'
            Write-DiagnosticText 'Not collected: device timestamp unavailable; unrelated or earlier logs were not saved.'
        }
        Write-DiagnosticText '[Startup-check result]'
        Write-DiagnosticText "Activity launch returned Status: ok: $launchOk"
        Write-DiagnosticText "HealthSync process alive after 10 seconds: $processAlive"
        Write-DiagnosticText "Crash-buffer read succeeded: $crashReadOk"
        Write-DiagnosticText "HealthSync startup crash detected: $appCrash"
        $startupFailed = -not $launchOk -or -not $processAlive -or $appCrash
        Write-Host "Activity launch OK: $launchOk; process alive: $processAlive; HealthSync crash found: $appCrash"
        if (-not $crashReadOk) { Write-Warning 'Startup crash-buffer verification is unavailable; see the report for the exact command result.' }
    }
    Write-DiagnosticText ('Completed: ' + [DateTimeOffset]::Now.ToString('o'))
    Write-DiagnosticText "Install returned Success: $success"
    Write-Host "Saved exact result: $script:diagnosticReportPath"
    if ($success -and $startupFailed) { Write-Warning 'APK installed, but the startup check failed. Share this report to diagnose the app error.'; exit 2 }
    if ($success) { Write-Host 'Watch APK installed successfully.'; exit 0 }
    Write-Warning 'Installation did not return Success. Share the report so its exact rejection can be diagnosed.'
    exit 1
} catch {
    Write-DiagnosticText ('Diagnostic stopped: ' + $_.Exception.Message)
    Write-Error -Message $_.Exception.Message -ErrorAction Continue
    if ($script:diagnosticReportPath) { Write-Host "Report: $script:diagnosticReportPath" }
    exit 1
}

