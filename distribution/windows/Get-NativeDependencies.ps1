[CmdletBinding()]
param(
    [string]$ManifestPath,
    [string]$OutputDirectory,
    [string]$CacheDirectory
)

$ErrorActionPreference = "Stop"
if (-not $ManifestPath) { $ManifestPath = Join-Path $PSScriptRoot "native-dependencies.json" }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot "..\..\target\native\windows-x64" }
if (-not $CacheDirectory) { $CacheDirectory = Join-Path $PSScriptRoot "..\..\target\downloads" }

function Assert-UnderDirectory {
    param([string]$Path, [string]$Parent)
    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $resolvedParent = [IO.Path]::GetFullPath($Parent).TrimEnd('\') + '\'
    if (-not $resolvedPath.StartsWith($resolvedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing filesystem operation outside $resolvedParent`: $resolvedPath"
    }
}

function Get-VerifiedArchive {
    param($Dependency, [string]$Destination)

    if (-not (Test-Path -LiteralPath $Destination)) {
        Write-Host "Downloading $($Dependency.archiveName)"
        Invoke-WebRequest -Uri $Dependency.url -OutFile $Destination
    }

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
    if ($actual -ne $Dependency.sha256.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $Destination. Expected $($Dependency.sha256), got $actual."
    }
}

function Expand-Dependency {
    param(
        $Dependency,
        [string]$Name,
        [string]$Destination
    )

    $archive = Join-Path $CacheDirectory $Dependency.archiveName
    Get-VerifiedArchive -Dependency $Dependency -Destination $archive

    $extractRoot = Join-Path $CacheDirectory ("extract-" + $Name)
    Assert-UnderDirectory -Path $extractRoot -Parent $CacheDirectory
    if (Test-Path -LiteralPath $extractRoot) {
        Remove-Item -LiteralPath $extractRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $extractRoot | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $extractRoot

    $source = Join-Path $extractRoot $Dependency.extractedDirectory
    if (-not (Test-Path -LiteralPath $source -PathType Container)) {
        throw "Expected extracted directory is missing: $source"
    }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Copy-Item -Path (Join-Path $source "*") -Destination $Destination -Recurse -Force
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
$targetRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\target"))
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$CacheDirectory = [IO.Path]::GetFullPath($CacheDirectory)
Assert-UnderDirectory -Path $OutputDirectory -Parent $targetRoot
Assert-UnderDirectory -Path $CacheDirectory -Parent $targetRoot

New-Item -ItemType Directory -Path $CacheDirectory -Force | Out-Null
if (Test-Path -LiteralPath $OutputDirectory) {
    Remove-Item -LiteralPath $OutputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null

Expand-Dependency -Dependency $manifest.vlc -Name "vlc" -Destination (Join-Path $OutputDirectory "vlc")
Expand-Dependency -Dependency $manifest.ffmpeg -Name "ffmpeg" -Destination (Join-Path $OutputDirectory "ffmpeg")

$required = @(
    (Join-Path $OutputDirectory "vlc\libvlc.dll"),
    (Join-Path $OutputDirectory "vlc\plugins"),
    (Join-Path $OutputDirectory "ffmpeg\bin\ffmpeg.exe"),
    (Join-Path $OutputDirectory "ffmpeg\bin\ffprobe.exe")
)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Native dependency assembly is incomplete: $path"
    }
}

Copy-Item -LiteralPath $ManifestPath -Destination (Join-Path $OutputDirectory "native-dependencies.json")
Write-Host "Native dependencies assembled in $OutputDirectory"
