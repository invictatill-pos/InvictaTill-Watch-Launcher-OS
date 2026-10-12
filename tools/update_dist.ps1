[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+$')][string]$WatchVersion = '2.7.1',
    [ValidatePattern('^\d+\.\d+\.\d+$')][string]$PhoneVersion = '2.7.1'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspaceDirectory = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $workspaceDirectory 'WatchApp/app/build/outputs/apk/debug'
$metadata = Get-Content -LiteralPath (Join-Path $outputDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne 'com.healthsync.watch' -or $metadata.elements.Count -ne 1 -or
    $metadata.elements[0].versionName -ne $WatchVersion) {
    throw "Build metadata does not match HealthSync Watch $WatchVersion. Assemble the debug APK first."
}
$sourceApk = Join-Path $outputDirectory $metadata.elements[0].outputFile
$distDirectory = Join-Path $workspaceDirectory 'dist'
$watchApk = Join-Path $distDirectory "HealthSync-Watch-$WatchVersion-Android8.apk"
$phoneOutput = Join-Path $workspaceDirectory 'PhoneApp/app/build/outputs/apk/release'
$phoneMetadata = Get-Content -LiteralPath (Join-Path $phoneOutput 'output-metadata.json') -Raw | ConvertFrom-Json
if ($phoneMetadata.applicationId -ne 'com.healthsync.phone' -or $phoneMetadata.elements.Count -ne 1 -or
    $phoneMetadata.elements[0].versionName -ne $PhoneVersion) {
    throw "Build metadata does not match HealthSync Phone $PhoneVersion. Assemble the release APK first."
}
$phoneApk = Join-Path $distDirectory "HealthSync-Phone-$PhoneVersion.apk"
Copy-Item -LiteralPath (Join-Path $phoneOutput $phoneMetadata.elements[0].outputFile) -Destination $phoneApk -Force
Copy-Item -LiteralPath (Join-Path $workspaceDirectory 'docs/LAUNCHER-ROADMAP.md') -Destination $distDirectory -Force
Copy-Item -LiteralPath (Join-Path $workspaceDirectory 'docs/COMMUNICATION-SETUP.md') -Destination $distDirectory -Force
Copy-Item -LiteralPath (Join-Path $workspaceDirectory 'docs/HEALTH-DATA-CONTRACT.md') -Destination $distDirectory -Force
Copy-Item -LiteralPath (Join-Path $workspaceDirectory 'docs/WATCH-HEALTH-FALLBACK.md') -Destination $distDirectory -Force
Copy-Item -LiteralPath $sourceApk -Destination $watchApk -Force
Copy-Item -LiteralPath $watchApk -Destination (Join-Path $distDirectory 'HealthSync-Watch.apk') -Force
Copy-Item -LiteralPath $phoneApk -Destination (Join-Path $distDirectory 'HealthSync-Phone.apk') -Force

# Generate updater metadata from the exact built artifacts. Stable tag addresses
# keep older clients from downloading a newer APK against a cached checksum.
$manifestPath = Join-Path $workspaceDirectory 'version.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$releaseBase = "https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/download/v$WatchVersion"
$manifest.phone.versionCode = $phoneMetadata.elements[0].versionCode
$manifest.phone.versionName = $PhoneVersion
$manifest.phone.apkUrl = "$releaseBase/HealthSync-Phone.apk"
$manifest.phone.sha256 = (Get-FileHash -LiteralPath $phoneApk -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest.watch.versionCode = $metadata.elements[0].versionCode
$manifest.watch.versionName = $WatchVersion
$manifest.watch.apkUrl = "$releaseBase/HealthSync-Watch.apk"
$manifest.watch.sha256 = (Get-FileHash -LiteralPath $watchApk -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 8) + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $distDirectory 'version.json') -Force

$checksumPath = Join-Path $distDirectory 'SHA256SUMS-Android8.txt'
$checksums = @($watchApk, $phoneApk) | ForEach-Object {
    '{0}  {1}' -f (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant(), (Split-Path -Leaf $_)
}
[System.IO.File]::WriteAllLines($checksumPath, $checksums, [System.Text.UTF8Encoding]::new($false))
$aliasChecksums = @('HealthSync-Watch.apk', 'HealthSync-Phone.apk') | ForEach-Object {
    '{0}  {1}' -f (Get-FileHash -LiteralPath (Join-Path $distDirectory $_) -Algorithm SHA256).Hash.ToLowerInvariant(), $_
}
[System.IO.File]::WriteAllLines((Join-Path $distDirectory 'SHA256SUMS.txt'), $aliasChecksums, [System.Text.UTF8Encoding]::new($false))
$bundleFiles = @(
    $watchApk, $phoneApk, $checksumPath, (Join-Path $distDirectory 'version.json'),
    (Join-Path $distDirectory 'ANDROID8-INSTALL.md'),
    (Join-Path $distDirectory 'COMMUNICATION-SETUP.md'),
    (Join-Path $distDirectory 'HEALTH-DATA-CONTRACT.md'),
    (Join-Path $distDirectory 'WATCH-HEALTH-FALLBACK.md'),
    (Join-Path $distDirectory "BUILD-REPORT-Android8-$WatchVersion.md"),
    (Join-Path $distDirectory 'LAUNCHER-ROADMAP.md'),
    (Join-Path $PSScriptRoot 'Install-Watch-Diagnostic.ps1'),
    (Join-Path $PSScriptRoot 'Install-Watch-Diagnostic.md'),
    (Join-Path $PSScriptRoot 'Collect-Watch-Sensor-Diagnostic.ps1'),
    (Join-Path $PSScriptRoot 'Collect-Watch-Sensor-Diagnostic.md')
)
$versionedZip = Join-Path $distDirectory "HealthSync-Android8-$WatchVersion-Both-APKs.zip"
Compress-Archive -LiteralPath $bundleFiles -DestinationPath $versionedZip -Force
Copy-Item -LiteralPath $versionedZip -Destination (Join-Path $distDirectory 'HealthSync-Android8-Both-APKs.zip') -Force
Write-Output "Watch $WatchVersion (code $($metadata.elements[0].versionCode)) packaged."
$checksums | Write-Output
Get-Item -LiteralPath $watchApk, $versionedZip | Select-Object Name, Length
