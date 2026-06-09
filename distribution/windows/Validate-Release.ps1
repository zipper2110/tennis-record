[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$license = Join-Path $PSScriptRoot "..\..\LICENSE"
$licenseText = Get-Content -LiteralPath $license -Raw
if ($licenseText -notmatch "GNU GENERAL PUBLIC LICENSE" -or $licenseText -notmatch "Version 3") {
    throw "The repository LICENSE must contain the GNU GPL version 3 text."
}
$notice = Get-Content -LiteralPath (Join-Path $PSScriptRoot "..\..\LICENSE-NOTICE") -Raw
if ($notice -notmatch "either version 3 of the License, or \(at your option\) any\s+later version") {
    throw "LICENSE-NOTICE must grant GPL version 3 or later."
}

Write-Host "GPLv3-or-later distribution license validation passed."
