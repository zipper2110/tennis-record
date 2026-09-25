[CmdletBinding()]
param(
    [string]$OutputPath,
    [string]$PngDirectory
)

# Renders src/main/resources/icons/app-icon.svg into a multi-size Windows ICO.
# The SVG is the single source for the icon. Run this script again after you change the SVG.

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$svg = Join-Path $repoRoot "src\main\resources\icons\app-icon.svg"
if (-not $OutputPath) { $OutputPath = Join-Path $PSScriptRoot "assets\tennis-record.ico" }

$classpathFile = Join-Path ([IO.Path]::GetTempPath()) "tennis-record-jsvg-classpath.txt"
& mvn -q -f (Join-Path $repoRoot "pom.xml") dependency:build-classpath "-Dmdep.includeArtifactIds=jsvg" "-Dmdep.outputFile=$classpathFile"
if ($LASTEXITCODE -ne 0) { throw "Maven could not resolve the jsvg library." }
$classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
Remove-Item -LiteralPath $classpathFile -Force

$arguments = @("-Djava.awt.headless=true", "-cp", $classpath, (Join-Path $PSScriptRoot "AppIconRenderer.java"), $svg, $OutputPath)
if ($PngDirectory) { $arguments += $PngDirectory }
& java @arguments
if ($LASTEXITCODE -ne 0) { throw "Icon rendering failed." }

Write-Host "Generated multi-resolution icon: $OutputPath"
