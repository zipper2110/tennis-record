$here = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $here "Run-UiSmoke.ps1")

Describe "UI smoke fixture validation" {
    It "rejects a missing fixture" {
        $missing = Join-Path $TestDrive "missing.mp4"

        $failure = $null
        try { Assert-UiSmokeFixtureFile -FixturePath $missing } catch { $failure = $_ }
        if (-not $failure) { throw "Expected missing fixture validation to fail." }
        $failure.Exception.Message | Should Match "fixture was not found"
    }

    It "rejects a fixture larger than five MiB" {
        $oversized = Join-Path $TestDrive "oversized.mp4"
        $stream = [IO.File]::Create($oversized)
        try {
            $stream.SetLength((5MB) + 1)
        }
        finally {
            $stream.Dispose()
        }

        $failure = $null
        try { Assert-UiSmokeFixtureFile -FixturePath $oversized } catch { $failure = $_ }
        if (-not $failure) { throw "Expected oversized fixture validation to fail." }
        $failure.Exception.Message | Should Match "at most 5 MiB"
    }

    It "rejects probe metadata without H.264 video" {
        $metadata = '{"format":{"duration":"4.48","format_name":"mov,mp4,m4a,3gp,3g2,mj2"},"streams":[{"codec_type":"video","codec_name":"vp9"}]}'

        $failure = $null
        try { Assert-UiSmokeProbeMetadata -ProbeJson $metadata } catch { $failure = $_ }
        if (-not $failure) { throw "Expected codec validation to fail." }
        $failure.Exception.Message | Should Match "H.264"
    }

    It "rejects probe metadata outside the duration contract" {
        $metadata = '{"format":{"duration":"2.99","format_name":"mov,mp4,m4a,3gp,3g2,mj2"},"streams":[{"codec_type":"video","codec_name":"h264"}]}'

        $failure = $null
        try { Assert-UiSmokeProbeMetadata -ProbeJson $metadata } catch { $failure = $_ }
        if (-not $failure) { throw "Expected duration validation to fail." }
        $failure.Exception.Message | Should Match "between 3 and 15 seconds"
    }
}

Describe "UI smoke executable validation" {
    It "requires a supplied packaged executable to exist" {
        $missing = Join-Path $TestDrive "Tennis Record.exe"

        $failure = $null
        try { Resolve-UiSmokeExecutable -ExecutablePath $missing } catch { $failure = $_ }
        if (-not $failure) { throw "Expected executable validation to fail." }
        $failure.Exception.Message | Should Match "executable was not found"
    }

    It "returns the absolute path of a supplied executable" {
        $executable = Join-Path $TestDrive "Tennis Record.exe"
        Set-Content -LiteralPath $executable -Value "placeholder"

        $resolved = Resolve-UiSmokeExecutable -ExecutablePath $executable

        $resolved | Should Be ([IO.Path]::GetFullPath($executable))
    }
}

Describe "UI smoke isolated run lifecycle" {
    It "creates separate app-data and artifact directories below a unique run root" {
        $runsRoot = Join-Path $TestDrive "runs"
        $reportsRoot = Join-Path $TestDrive "reports"

        $session = New-UiSmokeSession -RunsRoot $runsRoot -ReportsRoot $reportsRoot

        $session.RunRoot | Should Exist
        $session.AppDataDirectory | Should Exist
        $session.ArtifactDirectory | Should Exist
        $session.ReportPath | Should Exist
        $session.AppDataDirectory.StartsWith($session.RunRoot, [StringComparison]::OrdinalIgnoreCase) | Should Be $true
        $session.ArtifactDirectory.StartsWith($session.RunRoot, [StringComparison]::OrdinalIgnoreCase) | Should Be $true
    }

    It "injects app data only into the launched child process" {
        $appData = Join-Path $TestDrive "isolated-app-data"
        New-Item -ItemType Directory -Path $appData | Out-Null
        $capture = Join-Path $TestDrive "child-environment.txt"
        $childScript = Join-Path $TestDrive "capture-environment.ps1"
        Set-Content -LiteralPath $childScript -Value 'param($OutputPath) [IO.File]::WriteAllText($OutputPath, $env:TENNIS_RECORD_APP_DATA_DIR)'
        $parentBefore = $env:TENNIS_RECORD_APP_DATA_DIR
        $arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "{0}" "{1}"' -f $childScript, $capture

        $exitCode = Start-UiSmokeChildProcess `
            -ExecutablePath (Get-Process -Id $PID).Path `
            -Arguments $arguments `
            -AppDataDirectory $appData

        $exitCode | Should Be 0
        (Get-Content -LiteralPath $capture -Raw) | Should Be ([IO.Path]::GetFullPath($appData))
        $env:TENNIS_RECORD_APP_DATA_DIR | Should Be $parentBefore
    }

    It "removes a passing run while preserving its report" {
        $session = New-UiSmokeSession `
            -RunsRoot (Join-Path $TestDrive "runs") `
            -ReportsRoot (Join-Path $TestDrive "reports")

        Complete-UiSmokeSession -Session $session -Succeeded $true

        $session.RunRoot | Should Not Exist
        $session.ReportPath | Should Exist
    }

    It "retains a failed run and its report" {
        $session = New-UiSmokeSession `
            -RunsRoot (Join-Path $TestDrive "runs") `
            -ReportsRoot (Join-Path $TestDrive "reports")

        Complete-UiSmokeSession -Session $session -Succeeded $false

        $session.RunRoot | Should Exist
        $session.ReportPath | Should Exist
    }
}
