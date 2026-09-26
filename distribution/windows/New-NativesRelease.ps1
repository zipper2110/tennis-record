[CmdletBinding()]
param(
    # The tag of the natives release on GitHub.
    [string]$ReleaseTag = ("natives-" + (Get-Date -Format "yyyy-MM")),
    # Skip the FFmpeg source collection (for a quick test of the other steps).
    [switch]$SkipFfmpegSource,
    # Keep the FFmpeg stage archives of an earlier run and collect only the missing stages.
    [switch]$Resume
)

# Prepares the files of a natives release in target\natives-release:
# the pinned FFmpeg and libmpv archives from native-dependencies.json and their
# corresponding source. Upload the files to a draft GitHub release by hand.
# The FFmpeg source collection runs in Docker (Docker Desktop must run).

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$targetRoot = Join-Path $repoRoot "target"
$manifestPath = Join-Path $PSScriptRoot "native-dependencies.json"
$outDir = Join-Path $targetRoot "natives-release"
$workDir = Join-Path $targetRoot "natives-work"
$cacheDir = Join-Path $targetRoot "downloads"
# A GitHub release asset must be smaller than 2 GiB.
$maxAssetBytes = 2000000000
$partBytes = 1900MB

function Assert-UnderTarget([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($targetRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing filesystem operation outside $targetRoot`: $resolved"
    }
}

function Reset-Directory([string]$Path) {
    Assert-UnderTarget $Path
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Recurse -Force }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

# Downloads a file to the cache (if it is not there), checks the SHA-256 and
# copies it to the output directory.
function Save-Verified([string]$Url, [string]$Sha256, [string]$FileName) {
    $cached = Join-Path $cacheDir $FileName
    if (-not (Test-Path -LiteralPath $cached)) {
        Write-Host "Downloading $FileName"
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $cached
    }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $cached).Hash.ToLowerInvariant()
    if ($actual -ne $Sha256.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $FileName. Expected $Sha256, got $actual."
    }
    Copy-Item -LiteralPath $cached -Destination (Join-Path $outDir $FileName) -Force
}

# Runs a native command. Windows PowerShell 5.1 makes stderr output a terminating
# error when ErrorActionPreference is Stop, and docker and tar write progress to
# stderr. The caller checks LASTEXITCODE.
function Invoke-Native([scriptblock]$Command) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { & $Command 2>&1 | ForEach-Object { Write-Host ([string]$(if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.TargetObject } else { $_ })) } } finally { $ErrorActionPreference = $previous }
}

# Splits a file into parts of $partBytes (name.part00, name.part01, ...).
function Split-LargeFile([string]$Path) {
    $buffer = New-Object byte[] (8MB)
    $reader = [IO.File]::OpenRead($Path)
    try {
        $index = 0
        while ($reader.Position -lt $reader.Length) {
            $writer = [IO.File]::Create(("{0}.part{1:D2}" -f $Path, $index))
            try {
                $written = 0L
                while ($written -lt $partBytes) {
                    $count = $reader.Read($buffer, 0, [int][Math]::Min($buffer.Length, $partBytes - $written))
                    if ($count -le 0) { break }
                    $writer.Write($buffer, 0, $count)
                    $written += $count
                }
            } finally { $writer.Dispose() }
            $index++
        }
    } finally { $reader.Dispose() }
    Remove-Item -LiteralPath $Path
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
Reset-Directory $outDir
New-Item -ItemType Directory -Path $cacheDir -Force | Out-Null

# Download from the upstream URL. The url fields point to the natives release,
# which does not exist yet when you pin new builds.
function Get-UpstreamUrl($Dependency, [string]$Field) {
    $upstreamField = "upstream" + $Field.Substring(0, 1).ToUpperInvariant() + $Field.Substring(1)
    if ($Dependency.$upstreamField) { return $Dependency.$upstreamField }
    return $Dependency.$Field
}

Save-Verified (Get-UpstreamUrl $manifest.ffmpeg "url") $manifest.ffmpeg.sha256 $manifest.ffmpeg.archiveName
Save-Verified (Get-UpstreamUrl $manifest.mpv "url") $manifest.mpv.sha256 $manifest.mpv.archiveName
Save-Verified (Get-UpstreamUrl $manifest.mpv "sourceUrl") $manifest.mpv.sourceSha256 $manifest.mpv.sourceArchiveName

$ffmpegSourceName = "ffmpeg-$($manifest.ffmpeg.version)-$($manifest.ffmpeg.variant)-source.tar"
if (-not $SkipFfmpegSource) {
    Invoke-Native { docker info --format "{{.ServerVersion}}" }
    if ($LASTEXITCODE -ne 0) { throw "Docker is not available. Start Docker Desktop and try again." }

    $sourceDir = Join-Path $workDir "ffmpeg-source"
    if ($Resume) {
        New-Item -ItemType Directory -Path $sourceDir -Force | Out-Null
    } else {
        Reset-Directory $workDir
        New-Item -ItemType Directory -Path $sourceDir | Out-Null
    }

    # Use the base of the BtbN build image: Ubuntu 26.04 with Autoconf 2.73 (the
    # LAME stage needs it). The librsvg stage needs cargo to vendor its crates.
    $collect = "export DEBIAN_FRONTEND=noninteractive && apt-get update -qq && " +
        "apt-get install -y -qq --no-install-recommends ca-certificates git curl wget jq xz-utils subversion autoconf automake libtool libtool-bin pkgconf gettext autopoint python3 meson cargo > /dev/null && " +
        "curl -fsSL -o /tmp/autoconf.deb https://archive.ubuntu.com/ubuntu/pool/main/a/autoconf/autoconf_2.73-2_all.deb && dpkg -i /tmp/autoconf.deb > /dev/null && " +
        "bash /repo/distribution/sources/collect-btbn-ffmpeg-sources.sh /repo/distribution/windows/native-dependencies.json /out"
    Write-Host "Collecting the FFmpeg corresponding source in Docker. This takes a long time."
    Invoke-Native { docker run --rm -e "XZ_OPT=-T0" -v "${repoRoot}:/repo:ro" -v "${sourceDir}:/out" ubuntu:26.04 bash -c $collect }
    if ($LASTEXITCODE -ne 0) { throw "The FFmpeg source collection failed (exit code $LASTEXITCODE)." }

    $sourceTar = Join-Path $outDir $ffmpegSourceName
    Invoke-Native { tar.exe -cf $sourceTar -C $sourceDir . }
    if ($LASTEXITCODE -ne 0) { throw "tar failed to pack the FFmpeg source." }
    if ((Get-Item -LiteralPath $sourceTar).Length -gt $maxAssetBytes) {
        Split-LargeFile $sourceTar
    }
}

Copy-Item -LiteralPath $manifestPath -Destination $outDir
Copy-Item -LiteralPath (Join-Path $repoRoot "distribution\THIRD-PARTY-NOTICES.txt") -Destination $outDir

$readme = @"
Tennis Record natives $ReleaseTag

Third-party native builds that Tennis Record bundles, and their
corresponding source code. This is not a Tennis Record release.

Binaries (unmodified copies of the pinned upstream builds):
- $($manifest.ffmpeg.archiveName)
  FFmpeg $($manifest.ffmpeg.version), BtbN $($manifest.ffmpeg.variant) build. License: GPL version 3 or later.
  Upstream: $(Get-UpstreamUrl $manifest.ffmpeg "url")
- $($manifest.mpv.archiveName)
  libmpv $($manifest.mpv.version). License: $($manifest.mpv.license).
  Upstream: $(Get-UpstreamUrl $manifest.mpv "url")

Corresponding source:
- $ffmpegSourceName
  The FFmpeg source at commit $($manifest.ffmpeg.sourceCommit), the BtbN build
  scripts at commit $($manifest.ffmpeg.buildCommit), and the pinned source of
  each library stage of the build. SOURCES.txt inside lists each stage.
  If the file is split into .partNN files, join them before you extract them:
    Windows:  copy /b $ffmpegSourceName.part* $ffmpegSourceName
    Linux:    cat $ffmpegSourceName.part* > $ffmpegSourceName
- $($manifest.mpv.sourceArchiveName)
  The libmpv build source: mpv, FFmpeg, libplacebo, the build recipe, and the
  source packages of the LGPL MSYS2 libraries.
  Upstream: $(Get-UpstreamUrl $manifest.mpv "sourceUrl")

native-dependencies.json pins each file by SHA-256. SHA256SUMS.txt lists the
SHA-256 of each file in this release. THIRD-PARTY-NOTICES.txt lists the
licenses.
"@
Set-Content -LiteralPath (Join-Path $outDir "README.txt") -Value $readme -Encoding utf8

$sums = Get-ChildItem -LiteralPath $outDir -File | Sort-Object Name | ForEach-Object {
    "{0} *{1}" -f (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant(), $_.Name
}
Set-Content -LiteralPath (Join-Path $outDir "SHA256SUMS.txt") -Value $sums -Encoding ascii

Write-Host ""
Write-Host "The natives release files are in $outDir"
Get-ChildItem -LiteralPath $outDir -File | Sort-Object Name | Format-Table Name, @{ Name = "MB"; Expression = { [Math]::Round($_.Length / 1MB, 1) } } -AutoSize | Out-String | Write-Host
Write-Host "Next: create the draft release '$ReleaseTag' on GitHub and upload all files. See distribution/windows/README.md."
