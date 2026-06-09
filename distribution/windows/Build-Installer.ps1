[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$appImage = Join-Path $repoRoot "target\package\app-image\Tennis Record"
$installerDirectory = Join-Path $repoRoot "target\package\installer"
$tempDirectory = Join-Path $repoRoot "target\package\jpackage-installer-temp"
$installerLicense = Join-Path $repoRoot "target\package\Tennis-Record-License.txt"
$packageVersion = ($Version -replace '^v', '') -replace '-.*$', ''

if (-not (Test-Path -LiteralPath (Join-Path $appImage "Tennis Record.exe"))) {
    throw "Signed application image is missing: $appImage"
}
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset 3 is required to build the Windows EXE installer."
}

$resolvedInstaller = [IO.Path]::GetFullPath($installerDirectory)
$resolvedTarget = [IO.Path]::GetFullPath((Join-Path $repoRoot "target")).TrimEnd('\') + '\'
if (-not $resolvedInstaller.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace installer output outside target: $resolvedInstaller"
}
if (Test-Path -LiteralPath $installerDirectory) {
    Remove-Item -LiteralPath $installerDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $installerDirectory -Force | Out-Null
@(
    Get-Content -LiteralPath (Join-Path $repoRoot "LICENSE-NOTICE")
    ""
    Get-Content -LiteralPath (Join-Path $repoRoot "LICENSE")
) | Set-Content -LiteralPath $installerLicense -Encoding utf8
$resolvedTemp = [IO.Path]::GetFullPath($tempDirectory)
if (-not $resolvedTemp.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace jpackage temp directory outside target: $resolvedTemp"
}
if (Test-Path -LiteralPath $tempDirectory) {
    Remove-Item -LiteralPath $tempDirectory -Recurse -Force
}
$arguments = @(
    "--type", "exe",
    "--name", "Tennis Record",
    "--app-image", $appImage,
    "--dest", $installerDirectory,
    "--temp", $tempDirectory,
    "--verbose",
    "--app-version", $packageVersion,
    "--vendor", "Tennis Record",
    "--description", "Turn tennis match recordings into compact scored videos.",
    "--copyright", "Copyright (c) 2026 Tennis Record",
    "--license-file", $installerLicense,
    "--win-per-user-install",
    "--win-menu",
    "--win-menu-group", "Tennis Record",
    "--win-shortcut",
    "--win-shortcut-prompt",
    "--win-upgrade-uuid", "fc715967-3696-4f32-8690-4df9742077c6"
)
& jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage EXE installer creation failed." }

$installer = Get-ChildItem -LiteralPath $installerDirectory -Filter "*.exe" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $installer) { throw "jpackage did not produce an EXE installer." }
Write-Host "Installer created: $($installer.FullName)"
