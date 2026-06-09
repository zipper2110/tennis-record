[CmdletBinding()]
param(
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
if (-not $OutputPath) { $OutputPath = Join-Path $PSScriptRoot "assets\tennis-record-temp.ico" }
Add-Type -AssemblyName System.Drawing

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$images = @()

foreach ($size in $sizes) {
    $bitmap = New-Object Drawing.Bitmap $size, $size
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([Drawing.Color]::Transparent)

    $margin = [Math]::Max(1, [int]($size * 0.06))
    $background = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 18, 24, 31))
    $graphics.FillRectangle($background, $margin, $margin, $size - 2 * $margin, $size - 2 * $margin)

    $ballMargin = [int]($size * 0.18)
    $ball = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 161, 254, 0))
    $graphics.FillEllipse($ball, $ballMargin, $ballMargin, $size - 2 * $ballMargin, $size - 2 * $ballMargin)

    $penWidth = [Math]::Max(1.0, $size * 0.045)
    $seam = New-Object Drawing.Pen ([Drawing.Color]::White), $penWidth
    $seam.StartCap = [Drawing.Drawing2D.LineCap]::Round
    $seam.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $arcInset = [int]($size * 0.08)
    $graphics.DrawArc($seam, -$size / 3, $arcInset, $size, $size - 2 * $arcInset, -65, 130)
    $graphics.DrawArc($seam, $size / 3, $arcInset, $size, $size - 2 * $arcInset, 115, 130)

    $stream = New-Object IO.MemoryStream
    $bitmap.Save($stream, [Drawing.Imaging.ImageFormat]::Png)
    $images += ,$stream.ToArray()

    $seam.Dispose()
    $ball.Dispose()
    $background.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $stream.Dispose()
}

$parent = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $parent -Force | Out-Null
$file = [IO.File]::Open($OutputPath, [IO.FileMode]::Create)
$writer = New-Object IO.BinaryWriter $file
try {
    $writer.Write([UInt16]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]$images.Count)

    $offset = 6 + (16 * $images.Count)
    for ($i = 0; $i -lt $images.Count; $i++) {
        $size = $sizes[$i]
        $writer.Write([Byte]($(if ($size -eq 256) { 0 } else { $size })))
        $writer.Write([Byte]($(if ($size -eq 256) { 0 } else { $size })))
        $writer.Write([Byte]0)
        $writer.Write([Byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$images[$i].Length)
        $writer.Write([UInt32]$offset)
        $offset += $images[$i].Length
    }
    foreach ($image in $images) {
        $writer.Write($image)
    }
} finally {
    $writer.Dispose()
    $file.Dispose()
}

Write-Host "Generated temporary multi-resolution icon: $OutputPath"
