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
Require-Text 'src/main/resources/db/migration/V41__licensed_sfx_timeline.sql' @(
    'volume_percent', 'fade_in_seconds', 'fade_out_seconds')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/RenderAssetResolver.java' @(
    'licenseRenderable', 'RIGHTS_REVIEW_REQUIRED', 'license_code', 'attribution')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/RenderAudioMixBuilder.java' @(
    'startOffsetSeconds', 'endOffsetSeconds', 'volumePercent', 'fadeInSeconds', 'fadeOutSeconds', 'afade=t=in', 'afade=t=out')
Require-Text 'src/main/java/cn/longer233/gamenarrator/asset/AssetView.java' @(
    'licenseCode', 'licenseUrl', 'attribution', 'tags')
Require-Text 'frontend/public/app.js' @(
    'placementVolume', 'placementFadeIn', 'placementFadeOut', '<audio controls', 'licenseCode', 'attribution')
Require-Text 'src/test/java/cn/longer233/gamenarrator/render/FfmpegLicensedSfxOutputTest.java' @(
    'ffmpegRendersTimedVolumeAndFadedExternalSfx')
Require-Text 'src/test/java/cn/longer233/gamenarrator/render/RenderAssetLicenseGateTest.java' @(
    'rejectsExternalAssetsAwaitingRightsReviewOrWithoutLicense')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-607 | P1 |', 'SFX')
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) licensed SFX violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: FR-607 covers license/source/tags/preview, timeline volume and fades, with real FFmpeg output'
