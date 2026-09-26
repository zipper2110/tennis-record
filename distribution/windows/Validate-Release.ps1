[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Join-Path $PSScriptRoot "..\.."

function Read-RepoFile([string]$RelativePath) {
    return Get-Content -LiteralPath (Join-Path $repoRoot $RelativePath) -Raw -Encoding utf8
}

$licenseText = Read-RepoFile "LICENSE"
if ($licenseText -notmatch "^Elastic License 2\.0" -or
    $licenseText -notmatch "You may not move, change, disable, or circumvent the license key functionality") {
    throw "The repository LICENSE must contain the Elastic License 2.0 text."
}

$notice = Read-RepoFile "LICENSE-NOTICE"
foreach ($required in @(
    "Copyright \(c\) \d{4} Dmitrii Litvin",
    "Elastic License 2\.0",
    "LICENSE KEY FUNCTIONALITY",
    "THIRD-PARTY COMPONENTS",
    '"last-gpl"'
)) {
    if ($notice -notmatch $required) {
        throw "LICENSE-NOTICE must contain '$required'."
    }
}

$pom = Read-RepoFile "pom.xml"
if ($pom -notmatch "<name>Elastic License 2\.0</name>") {
    throw "pom.xml must declare the Elastic License 2.0."
}

# The app loads libmpv into its own process. Only an LGPL build is compatible with ELv2.
$manifest = Read-RepoFile "distribution\windows\native-dependencies.json" | ConvertFrom-Json
if ($manifest.mpv.license -notmatch "^LGPL-") {
    throw "native-dependencies.json mpv.license must be an LGPL license: '$($manifest.mpv.license)'"
}
# The GPL and the LGPL require the corresponding source. The natives release of this
# repository keeps the bundled binaries and their source together.
if ($manifest.nativesRelease.tag -notmatch "^natives-") {
    throw "native-dependencies.json nativesRelease.tag must name a natives-* release."
}
$nativesDownload = "https://github.com/zipper2110/tennis-record/releases/download/$($manifest.nativesRelease.tag)/"
foreach ($name in @("mpv", "ffmpeg")) {
    $dependency = $manifest.$name
    foreach ($field in @("sourceUrl", "sourceSha256", "sourceArchiveName")) {
        if (-not $dependency.$field) {
            throw "native-dependencies.json $name.$field is required."
        }
    }
    foreach ($pair in @(@("url", "archiveName"), @("sourceUrl", "sourceArchiveName"))) {
        $expected = $nativesDownload + $dependency.($pair[1])
        if ($dependency.($pair[0]) -ne $expected) {
            throw "native-dependencies.json $name.$($pair[0]) must be $expected"
        }
    }
}

$thirdParty = Read-RepoFile "distribution\THIRD-PARTY-NOTICES.txt"
if ($thirdParty -notmatch [regex]::Escape($manifest.mpv.variant)) {
    throw "THIRD-PARTY-NOTICES.txt must name the bundled libmpv variant '$($manifest.mpv.variant)'."
}
if ($thirdParty -notmatch "GNU Lesser General Public License" -or $thirdParty -notmatch "License: Elastic License 2\.0") {
    throw "THIRD-PARTY-NOTICES.txt must list libmpv under the LGPL and Tennis Record under ELv2."
}
if ($thirdParty -notmatch [regex]::Escape($manifest.nativesRelease.url)) {
    throw "THIRD-PARTY-NOTICES.txt must link the natives release $($manifest.nativesRelease.url)."
}

Write-Host "Elastic License 2.0 distribution license validation passed."
