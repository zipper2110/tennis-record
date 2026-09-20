[CmdletBinding()]
param(
    [string]$ExecutablePath,
    [switch]$KeepArtifacts,
    [string]$ReportPath,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"

function Resolve-UiSmokeLiteralPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$AllowMissing
    )

    if (-not $AllowMissing -and -not (Test-Path -LiteralPath $Path)) {
        throw "UI smoke path was not found: $Path"
    }

    return [IO.Path]::GetFullPath($Path)
}

function Test-UiSmokeChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Parent
    )

    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $trimCharacters = [char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $resolvedParent = [IO.Path]::GetFullPath($Parent).TrimEnd($trimCharacters) + [IO.Path]::DirectorySeparatorChar
    return $resolvedPath.StartsWith($resolvedParent, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-UiSmokeFixtureFile {
    param([Parameter(Mandatory = $true)][string]$FixturePath)

    if (-not (Test-Path -LiteralPath $FixturePath -PathType Leaf)) {
        throw "UI smoke fixture was not found: $FixturePath"
    }

    $fixture = Get-Item -LiteralPath $FixturePath
    if ($fixture.Length -gt 5MB) {
        throw "UI smoke fixture must be at most 5 MiB: $($fixture.FullName)"
    }
    if ($fixture.Extension -ine ".mp4") {
        throw "UI smoke fixture must be an MP4 file: $($fixture.FullName)"
    }

    return $fixture.FullName
}

function Assert-UiSmokeProbeMetadata {
    param([Parameter(Mandatory = $true)][string]$ProbeJson)

    try {
        $probe = $ProbeJson | ConvertFrom-Json
    }
    catch {
        throw "UI smoke ffprobe metadata is not valid JSON: $($_.Exception.Message)"
    }

    $video = @($probe.streams | Where-Object { $_.codec_type -eq "video" })
    if (-not ($video | Where-Object { $_.codec_name -eq "h264" })) {
        throw "UI smoke fixture must contain H.264 video."
    }

    $duration = 0.0
    if (-not [double]::TryParse(
            [string]$probe.format.duration,
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$duration
        )) {
        throw "UI smoke fixture duration is missing or invalid."
    }
    if ($duration -lt 3 -or $duration -gt 15) {
        throw "UI smoke fixture duration must be between 3 and 15 seconds; was $duration."
    }
    if ([string]$probe.format.format_name -notmatch "mp4") {
        throw "UI smoke fixture must be an MP4 container."
    }
}

function Invoke-UiSmokeProbe {
    param([Parameter(Mandatory = $true)][string]$FixturePath)

    $ffprobe = Get-Command ffprobe.exe -ErrorAction SilentlyContinue
    if (-not $ffprobe) {
        $ffprobe = Get-Command ffprobe -ErrorAction SilentlyContinue
    }
    if (-not $ffprobe) {
        throw "ffprobe was not found. Install FFmpeg or run the packaged smoke from an app image that provides it."
    }

    $probeJson = & $ffprobe.Source -v error -show_entries "format=duration,format_name,size" -show_entries "stream=codec_name,codec_type" -of json -- $FixturePath
    if ($LASTEXITCODE -ne 0) {
        throw "ffprobe failed for UI smoke fixture: $FixturePath"
    }
    Assert-UiSmokeProbeMetadata -ProbeJson ($probeJson -join [Environment]::NewLine)
}

function Resolve-UiSmokeExecutable {
    param([Parameter(Mandatory = $true)][string]$ExecutablePath)

    if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) {
        throw "UI smoke executable was not found: $ExecutablePath"
    }
    return (Get-Item -LiteralPath $ExecutablePath).FullName
}

function New-UiSmokeSession {
    param(
        [Parameter(Mandatory = $true)][string]$RunsRoot,
        [Parameter(Mandatory = $true)][string]$ReportsRoot,
        [string]$ReportPath
    )

    $resolvedRunsRoot = Resolve-UiSmokeLiteralPath -Path $RunsRoot -AllowMissing
    $resolvedReportsRoot = Resolve-UiSmokeLiteralPath -Path $ReportsRoot -AllowMissing
    New-Item -ItemType Directory -Path $resolvedRunsRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $resolvedReportsRoot -Force | Out-Null

    $runRoot = Join-Path $resolvedRunsRoot ("run-{0:yyyyMMdd-HHmmss}-{1}" -f (Get-Date), [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $runRoot | Out-Null
    $appDataDirectory = Join-Path $runRoot "app-data"
    $artifactDirectory = Join-Path $runRoot "artifacts"
    New-Item -ItemType Directory -Path $appDataDirectory | Out-Null
    New-Item -ItemType Directory -Path $artifactDirectory | Out-Null

    if ($ReportPath) {
        $resolvedReportPath = Resolve-UiSmokeLiteralPath -Path $ReportPath -AllowMissing
        $reportDirectory = Split-Path -Parent $resolvedReportPath
        if ($reportDirectory) {
            New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
        }
    }
    else {
        $resolvedReportPath = Join-Path $resolvedReportsRoot ("ui-smoke-{0:yyyyMMdd-HHmmss}-{1}.md" -f (Get-Date), [Guid]::NewGuid().ToString("N"))
    }

    @"
# Packaged Windows UI Smoke Report

Started: $(Get-Date -Format o)

## Scenario results

| Step | Status | Screenshot path | Expected result | Actual result |
| --- | --- | --- | --- | --- |
| Projects launch | Pending |  |  |  |
| Import fixture | Pending |  |  |  |
| Video playback | Pending |  |  |  |
| Editing and scoring | Pending |  |  |  |
| FFmpeg export | Pending |  |  |  |
| Relaunch and recents | Pending |  |  |  |

## Failure details

- Reproduction steps:
- Severity:
- Triage summary:
"@ | Set-Content -LiteralPath $resolvedReportPath -Encoding utf8

    return [pscustomobject]@{
        RunsRoot = $resolvedRunsRoot
        RunRoot = [IO.Path]::GetFullPath($runRoot)
        AppDataDirectory = [IO.Path]::GetFullPath($appDataDirectory)
        ArtifactDirectory = [IO.Path]::GetFullPath($artifactDirectory)
        ReportPath = [IO.Path]::GetFullPath($resolvedReportPath)
    }
}

function Start-UiSmokeChildProcess {
    param(
        [Parameter(Mandatory = $true)][string]$ExecutablePath,
        [string]$Arguments = "",
        [Parameter(Mandatory = $true)][string]$AppDataDirectory
    )

    $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $ExecutablePath
    $resolvedAppData = Resolve-UiSmokeLiteralPath -Path $AppDataDirectory
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $resolvedExecutable
    $startInfo.Arguments = $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.EnvironmentVariables["TENNIS_RECORD_APP_DATA_DIR"] = $resolvedAppData

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "UI smoke process did not start: $resolvedExecutable"
    }
    $process.WaitForExit()
    return $process.ExitCode
}

function Complete-UiSmokeSession {
    param(
        [Parameter(Mandatory = $true)]$Session,
        [Parameter(Mandatory = $true)][bool]$Succeeded,
        [switch]$KeepArtifacts
    )

    if (-not $Succeeded -or $KeepArtifacts) {
        return
    }
    if (-not (Test-UiSmokeChildPath -Path $Session.RunRoot -Parent $Session.RunsRoot)) {
        throw "Refusing to clean a UI smoke run outside the runner-created runs directory: $($Session.RunRoot)"
    }
    if (Test-Path -LiteralPath $Session.RunRoot) {
        Remove-Item -LiteralPath $Session.RunRoot -Recurse -Force
    }
}

function Write-UiSmokeReportStatus {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][string]$Status,
        [string]$Detail
    )

    "`nRunner status: $Status`nDetail: $Detail`nFinished: $(Get-Date -Format o)" |
        Add-Content -LiteralPath $ReportPath -Encoding utf8
}

function Invoke-UiSmokeRunner {
    $repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
    $fixturePath = Join-Path $repoRoot "src\test\resources\media\ui-smoke.mp4"
    $targetRoot = Join-Path $repoRoot "target\ui-smoke"
    $runsRoot = Join-Path $targetRoot "runs"
    $reportsRoot = Join-Path $targetRoot "reports"

    $fixturePath = Assert-UiSmokeFixtureFile -FixturePath $fixturePath
    Invoke-UiSmokeProbe -FixturePath $fixturePath

    if ($ExecutablePath) {
        $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $ExecutablePath
    }
    else {
        $defaultExecutable = Join-Path $repoRoot "target\package\app-image\Tennis Record\Tennis Record.exe"
        if (-not (Test-Path -LiteralPath $defaultExecutable -PathType Leaf)) {
            & (Join-Path $repoRoot "distribution\windows\Build-AppImage.ps1")
            if ($LASTEXITCODE -ne 0) {
                throw "Windows app-image build failed."
            }
        }
        $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $defaultExecutable
    }

    $session = New-UiSmokeSession -RunsRoot $runsRoot -ReportsRoot $reportsRoot -ReportPath $ReportPath
    Write-Host "UI smoke fixture: $fixturePath"
    Write-Host "UI smoke report: $($session.ReportPath)"
    Write-Host "UI smoke artifacts: $($session.ArtifactDirectory)"
    Write-Host "UI smoke app data: $($session.AppDataDirectory)"

    if ($ValidateOnly) {
        Write-UiSmokeReportStatus -ReportPath $session.ReportPath -Status "Validated" -Detail "Fixture, probe metadata, and executable were validated; application was not launched."
        Complete-UiSmokeSession -Session $session -Succeeded $true -KeepArtifacts:$KeepArtifacts
        return
    }

    try {
        $exitCode = Start-UiSmokeChildProcess -ExecutablePath $resolvedExecutable -AppDataDirectory $session.AppDataDirectory
        if ($exitCode -ne 0) {
            throw "Packaged application exited with code $exitCode."
        }
        Write-UiSmokeReportStatus -ReportPath $session.ReportPath -Status "Process exited" -Detail "Complete the checklist before declaring the smoke scenario passed."
        Complete-UiSmokeSession -Session $session -Succeeded $true -KeepArtifacts:$KeepArtifacts
    }
    catch {
        Write-UiSmokeReportStatus -ReportPath $session.ReportPath -Status "Failed" -Detail $_.Exception.Message
        Write-Host "UI smoke failed; retained QA root: $($session.RunRoot)"
        throw
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    Invoke-UiSmokeRunner
}
