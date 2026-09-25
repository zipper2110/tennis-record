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
# The LGPL requires the corresponding source. The native-sources release job publishes it.
foreach ($field in @("sourceUrl", "sourceSha256", "sourceArchiveName")) {
    if (-not $manifest.mpv.$field) {
        throw "native-dependencies.json mpv.$field is required."
    }
}

$thirdParty = Read-RepoFile "distribution\THIRD-PARTY-NOTICES.txt"
if ($thirdParty -notmatch [regex]::Escape($manifest.mpv.variant)) {
    throw "THIRD-PARTY-NOTICES.txt must name the bundled libmpv variant '$($manifest.mpv.variant)'."
}
if ($thirdParty -notmatch "GNU Lesser General Public License" -or $thirdParty -notmatch "License: Elastic License 2\.0") {
    throw "THIRD-PARTY-NOTICES.txt must list libmpv under the LGPL and Tennis Record under ELv2."
}

Write-Host "Elastic License 2.0 distribution license validation passed."
