[CmdletBinding()]
param(
    [string]$Version,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$targetRoot = Join-Path $repoRoot "target"
$inputDirectory = Join-Path $targetRoot "distribution\input"
$nativeDirectory = Join-Path $targetRoot "native\windows-x64"
$packageRoot = Join-Path $targetRoot "package"
$appImageRoot = Join-Path $packageRoot "app-image"
$icon = Join-Path $PSScriptRoot "assets\tennis-record-temp.ico"

if (-not $IsWindows -and $PSVersionTable.PSEdition -eq "Core") {
    throw "The Windows application image must be built on Windows."
}
if (-not [Environment]::Is64BitOperatingSystem) {
    throw "Windows x64 is required."
}
if (-not (Get-Command jpackage.exe -ErrorAction SilentlyContinue)) {
    throw "jpackage.exe was not found. Install and select JDK 17."
}
$jdkRelease = Join-Path $env:JAVA_HOME "release"
if (-not (Test-Path -LiteralPath $jdkRelease)) {
    throw "JAVA_HOME must point to a JDK 17 installation."
}
$javaVersionLine = Get-Content -LiteralPath $jdkRelease | Where-Object { $_ -like "JAVA_VERSION=*" } | Select-Object -First 1
if ($javaVersionLine -notmatch 'JAVA_VERSION="17(\.|")') {
    throw "JAVA_HOME must point to JDK 17; found $javaVersionLine."
}

if (-not $Version) {
    $Version = (& mvn "-Dexpression=project.version" "-DforceStdout" -q help:evaluate | Select-Object -Last 1).Trim()
}
$packageVersion = ($Version -replace '^v', '') -replace '-.*$', ''
if ($packageVersion -notmatch '^\d+(\.\d+){0,3}$') {
    throw "jpackage requires a numeric version; got '$packageVersion'."
}

if (-not $SkipBuild) {
    $resolvedInput = [IO.Path]::GetFullPath($inputDirectory)
    $resolvedTargetForInput = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    if (-not $resolvedInput.StartsWith($resolvedTargetForInput, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace distribution input outside target: $resolvedInput"
    }
    if (Test-Path -LiteralPath $inputDirectory) {
        Remove-Item -LiteralPath $inputDirectory -Recurse -Force
    }
    & mvn -B -Pdistribution -DskipTests "-Drevision=$packageVersion" package
    if ($LASTEXITCODE -ne 0) { throw "Maven distribution build failed." }
}
if (-not (Test-Path -LiteralPath (Join-Path $nativeDirectory "vlc\libvlc.dll"))) {
    throw "Native dependencies are missing. Run distribution/windows/Get-NativeDependencies.ps1."
}
if (-not (Test-Path -LiteralPath $icon)) {
    & (Join-Path $PSScriptRoot "New-TemporaryIcon.ps1") -OutputPath $icon
}

$mainJar = Get-ChildItem -LiteralPath $inputDirectory -Filter "tennisrecord-*.jar" |
    Where-Object { $_.Name -notlike "*sources*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $mainJar) {
    throw "Application JAR was not found in $inputDirectory."
}

$resolvedAppImageRoot = [IO.Path]::GetFullPath($appImageRoot)
$resolvedTargetRoot = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
if (-not $resolvedAppImageRoot.StartsWith($resolvedTargetRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace app-image outside target: $resolvedAppImageRoot"
}
if (Test-Path -LiteralPath $appImageRoot) {
    Remove-Item -LiteralPath $appImageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $appImageRoot -Force | Out-Null

$arguments = @(
    "--type", "app-image",
    "--name", "Tennis Record",
    "--dest", $appImageRoot,
    "--input", $inputDirectory,
    "--main-jar", $mainJar.Name,
    "--main-class", "org.litvin.SwingMainApp",
    "--app-version", $packageVersion,
    "--vendor", "Tennis Record",
    "--description", "Turn tennis match recordings into compact scored videos.",
    "--copyright", "Copyright (c) 2026 Tennis Record",
    "--icon", $icon,
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "-Dtennis.record.version=$packageVersion"
)
& jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image creation failed." }

$appHome = Join-Path $appImageRoot "Tennis Record"
$javaCommand = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaCommand)) {
    throw "JDK 17 java.exe was not found: $javaCommand"
}
Copy-Item -LiteralPath $javaCommand -Destination (Join-Path $appHome "runtime\bin\java.exe")
New-Item -ItemType Directory -Path (Join-Path $appHome "natives") -Force | Out-Null
Copy-Item -LiteralPath $nativeDirectory -Destination (Join-Path $appHome "natives") -Recurse -Force
New-Item -ItemType Directory -Path (Join-Path $appHome "legal") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE") -Destination (Join-Path $appHome "legal\LICENSE.txt")
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE-NOTICE") -Destination (Join-Path $appHome "legal\LICENSE-NOTICE.txt")
Copy-Item -LiteralPath (Join-Path $repoRoot "distribution\THIRD-PARTY-NOTICES.txt") -Destination (Join-Path $appHome "legal\THIRD-PARTY-NOTICES.txt")
$diagnosticsTemplate = Get-Content -LiteralPath (Join-Path $PSScriptRoot "Tennis Record Diagnostics.cmd") -Raw
$diagnosticsTemplate.Replace("@APP_VERSION@", $packageVersion) |
    Set-Content -LiteralPath (Join-Path $appHome "Tennis Record Diagnostics.cmd") -Encoding ascii

Write-Host "Application image created: $appHome"
