param([string]$Output = (Join-Path $PSScriptRoot '..\launcher\assets\GameNarrator.ico'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$target = [IO.Path]::GetFullPath($Output)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
$bitmap = New-Object Drawing.Bitmap 256,256
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([Drawing.Color]::FromArgb(8,14,30))
$background = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(20,34,62))
$cyan = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(69,215,234))
$violet = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(168,104,255))
$white = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(244,249,255))
$graphics.FillEllipse($background,12,12,232,232)
$graphics.FillPie($violet,29,29,198,198,205,95)
$graphics.FillEllipse($background,45,45,166,166)
$play = [Drawing.PointF[]]@((New-Object Drawing.PointF 92,73),(New-Object Drawing.PointF 92,183),(New-Object Drawing.PointF 181,128))
$graphics.FillPolygon($cyan,$play)
$bars = @(56,39,68,30,51)
for($i=0;$i -lt $bars.Count;$i++) {
  $height=$bars[$i]; $x=55+($i*23); $graphics.FillRectangle($white,$x,128-($height/2),9,$height)
}
$handle=$bitmap.GetHicon()
try {
  $icon=[Drawing.Icon]::FromHandle($handle)
  $stream=[IO.File]::Create($target)
  try { $icon.Save($stream) } finally { $stream.Dispose(); $icon.Dispose() }
} finally {
  $graphics.Dispose(); $bitmap.Dispose()
  Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class IconNative { [DllImport("user32.dll")] public static extern bool DestroyIcon(IntPtr h); }'
  [IconNative]::DestroyIcon($handle) | Out-Null
}
Write-Host "GameNarrator icon generated: $target"
