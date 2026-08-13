param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()
function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $content = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) { if (-not $content.Contains($needle)) { $failures.Add("$relative missing contract: $needle") } }
}
Require-Text 'src/main/resources/db/migration/V40__meme_timeline_slots.sql' @(
    'start_offset_seconds', 'end_offset_seconds', 'scale_percent', 'animation_name', 'z_index')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/RenderVideoFilterBuilder.java' @(
    "enable='", 'scalePercent', 'animationFilter', 'BOUNCE', 'SLIDE', 'eof_action=pass')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/FfmpegVideoRenderer.java' @(
    '"MEME".equals(asset.assetType()) || "IMAGE".equals(asset.assetType())', '"-loop", "1"')
Require-Text 'frontend/public/app.js' @(
    'placementStart', 'placementEnd', 'placementScale', 'placementAnimation', 'placementZIndex')
Require-Text 'src/test/java/cn/longer233/gamenarrator/render/FfmpegMemeOverlayOutputTest.java' @(
    'ffmpegPreservesTransparentPngAndWebpInTimedLayeredOverlay', 'pixel(output, .2',
    'pixel(output, .8', 'pixel(output, 1.5', 'pixel(output, 2.7', 'assertBlue', 'assertRed')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-608 | P1 |', 'PNG/WebP')
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) meme slot violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: FR-608 supports local PNG/WebP timing, position, scaling, animation, layering and real FFmpeg overlay'
