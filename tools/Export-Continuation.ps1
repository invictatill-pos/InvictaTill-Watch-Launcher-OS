[CmdletBinding()]
param(
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$ExportDate = (Get-Date -Format 'yyyy-MM-dd')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$rootPrefix = $projectRoot.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
$exportFiles = @{}

function Add-ExportFile([string]$RelativePath, [string]$EntryPath = '') {
    $absolutePath = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $RelativePath))
    if (-not $absolutePath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Export file escaped project root: $RelativePath"
    }
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) { throw "Export file missing: $RelativePath" }
    $normalized = $RelativePath.Replace('\','/')
    if ($normalized -match '(^|/)(build|\.gradle|\.kotlin|\.idea|\.vscode)(/|$)' -or
        $normalized -match '(^|/)(local\.properties|\.env(?:\..*)?)$' -or
        $normalized -match '\.(keystore|jks|p12|pfx|pem)$') { throw "Excluded export file: $RelativePath" }
    if ([string]::IsNullOrEmpty($EntryPath)) { $EntryPath = $normalized }
    if ($EntryPath -match '(^|/)\.\.(/|$)' -or $EntryPath.StartsWith('/')) { throw 'Unsafe ZIP entry' }
    $exportFiles['HealthSync/' + $EntryPath.Replace('\','/')] = $absolutePath
}

function Add-ExportTree([string]$RelativeDirectory) {
    $directory = Join-Path $projectRoot $RelativeDirectory
    foreach ($file in (Get-ChildItem -LiteralPath $directory -Recurse -File -Force)) {
        Add-ExportFile $file.FullName.Substring($rootPrefix.Length)
    }
}

$moduleVersions = foreach ($module in @('WatchApp','PhoneApp')) {
    $buildType = if ($module -eq 'WatchApp') { 'debug' } else { 'release' }
    $metadataPath = Join-Path $projectRoot "$module/app/build/outputs/apk/$buildType/output-metadata.json"
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.elements.Count -ne 1) { throw "Expected one APK for $module" }
    $element = $metadata.elements[0]
    $buildText = Get-Content -LiteralPath (Join-Path $projectRoot "$module/app/build.gradle.kts") -Raw
    $sourceVersion = [regex]::Match($buildText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $sourceCode = [regex]::Match($buildText, 'versionCode\s*=\s*(\d+)').Groups[1].Value
    if ($sourceVersion -ne $element.versionName -or [int]$sourceCode -ne [int]$element.versionCode) {
        throw "Build metadata is stale for $module. Build the current source before exporting."
    }
    $deliveredName = if ($module -eq 'WatchApp') { "HealthSync-Watch-$sourceVersion-Android8.apk" }
        else { "HealthSync-Phone-$sourceVersion.apk" }
    $builtApk = Join-Path (Split-Path -Parent $metadataPath) $element.outputFile
    $deliveredApk = Join-Path $projectRoot ('dist/' + $deliveredName)
    if ((Get-FileHash -LiteralPath $builtApk).Hash -ne (Get-FileHash -LiteralPath $deliveredApk).Hash) {
        throw "Delivered $module APK differs from the build. Update dist before exporting."
    }
    [pscustomobject]@{ module=$module; applicationId=$metadata.applicationId; version=$sourceVersion;
        versionCode=[int]$sourceCode; apk=$deliveredName }
}
$watchVersion = ($moduleVersions | Where-Object module -eq 'WatchApp').version
$phoneVersion = ($moduleVersions | Where-Object module -eq 'PhoneApp').version
$handoff = Get-Content -LiteralPath (Join-Path $projectRoot 'docs/PROJECT-HANDOFF.md') -Raw
if (-not $handoff.Contains("Watch $watchVersion") -or -not $handoff.Contains("Phone $phoneVersion")) {
    throw 'Update PROJECT-HANDOFF.md for the current versions before exporting.'
}

foreach ($path in @('build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew.bat','README.md')) {
    Add-ExportFile $path
}
Add-ExportTree 'gradle'
foreach ($module in @('WatchApp','PhoneApp')) {
    Add-ExportFile "$module/build.gradle.kts"
    Add-ExportFile "$module/app/build.gradle.kts"
    Add-ExportFile "$module/app/proguard-rules.pro"
    Add-ExportTree "$module/app/src"
}
Add-ExportTree 'docs'
Add-ExportTree 'tools'
Add-ExportFile 'docs/PROJECT-HANDOFF.md' 'READ_FIRST.md'
foreach ($module in $moduleVersions) { Add-ExportFile ('dist/' + $module.apk) }
foreach ($path in @('SHA256SUMS-Android8.txt','ANDROID8-INSTALL.md','LAUNCHER-ROADMAP.md',
    "BUILD-REPORT-Android8-$watchVersion.md")) { Add-ExportFile ('dist/' + $path) }

$evidenceDirectory = "validation/watch-shell-$watchVersion"
$evidenceNames = @('checks.json','watch-output-metadata.json','phone-output-metadata.json',
    'watch-signature.txt','phone-signature.txt','ambient-wake-check.json','bundle-check.json',
    'home-square.png','controls-square.png','apps-square.png','fitness-square.png',
    'notifications-native.png','media-offline.png','apps-320-font130.png','controls-320-font130.png',
    'timer-home.png','timer-finished.png','ambient-square.png','stopwatch-ready.png')
foreach ($name in $evidenceNames) {
    $relativePath = "$evidenceDirectory/$name"
    if (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath) -PathType Leaf) { Add-ExportFile $relativePath }
}

$manifestFiles = foreach ($entry in ($exportFiles.Keys | Sort-Object)) {
    $source = Get-Item -LiteralPath $exportFiles[$entry]
    [pscustomobject]@{ path=$entry; bytes=$source.Length;
        sha256=(Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
}
$manifest = [ordered]@{
    format='HealthSync source and continuation snapshot v1'
    snapshotDate=$ExportDate
    createdAtUtc=[DateTime]::UtcNow.ToString('o')
    readFirst='HealthSync/READ_FIRST.md'
    applications=@($moduleVersions)
    fileCount=@($manifestFiles).Count
    exclusions=@('Build and dependency caches','local.properties','Private signing keys',
        'Emulator/system images and state','Device databases and health data','Historical APK deliveries')
    files=@($manifestFiles)
} | ConvertTo-Json -Depth 8

$outputPath = Join-Path $projectRoot "dist/HealthSync-Continuation-$ExportDate-Watch$watchVersion-Phone$phoneVersion.zip"
$fileStream = [System.IO.File]::Open($outputPath,[System.IO.FileMode]::Create,[System.IO.FileAccess]::ReadWrite)
try {
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream,[System.IO.Compression.ZipArchiveMode]::Create,$true)
    try {
        foreach ($entry in ($exportFiles.Keys | Sort-Object)) {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$exportFiles[$entry],$entry,
                [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
        $manifestEntry = $archive.CreateEntry('HealthSync/EXPORT-MANIFEST.json')
        $manifestStream = $manifestEntry.Open()
        try {
            $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($manifest)
            $manifestStream.Write($bytes,0,$bytes.Length)
        } finally { $manifestStream.Dispose() }
    } finally { $archive.Dispose() }
} finally { $fileStream.Dispose() }

$verificationArchive = [System.IO.Compression.ZipFile]::OpenRead($outputPath)
try {
    if ($verificationArchive.Entries.Count -ne @($manifestFiles).Count + 1) { throw 'Wrong archive file count' }
    foreach ($record in $manifestFiles) {
        $entry = $verificationArchive.GetEntry($record.path)
        if ($null -eq $entry -or $entry.Length -ne $record.bytes) { throw "Archive entry missing or wrong size: $($record.path)" }
        $entryStream = $entry.Open()
        $algorithm = [System.Security.Cryptography.SHA256]::Create()
        try { $hash = [System.BitConverter]::ToString($algorithm.ComputeHash($entryStream)).Replace('-','').ToLowerInvariant() }
        finally { $entryStream.Dispose(); $algorithm.Dispose() }
        if ($hash -ne $record.sha256) { throw "Archive hash mismatch: $($record.path)" }
    }
} finally { $verificationArchive.Dispose() }

$result = Get-Item -LiteralPath $outputPath
[pscustomobject]@{ path=$result.FullName; bytes=$result.Length; files=@($manifestFiles).Count + 1;
    sha256=(Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash.ToLowerInvariant(); verified=$true }
