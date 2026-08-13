param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()

function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) { if (-not $text.Contains($needle)) { $failures.Add("$relative missing contract: $needle") } }
}

Require-Text 'src/main/java/cn/longer233/gamenarrator/media/FfmpegMediaPreprocessor.java' @(
    'SCENE_CHANGE_WINDOW', 'AUDIO_PEAK_WINDOW', 'AUDIO_CLIPPING_WINDOW',
    'eventOffsets()', 'audioAnalysis.analyze(audioPath)')
Require-Text 'src/main/java/cn/longer233/gamenarrator/vision/AdaptiveFrameSampler.java' @(
    'eventCenters', 'isCenter', 'configuredMaximum')
Require-Text 'src/main/java/cn/longer233/gamenarrator/vision/EventWindowSecondPass.java' @(
    'analysis.excitementScore() >= 60', '!analysis.ocrText().isBlank()',
    'new EventBudget(6, 100, false)', 'new EventBudget(6, 90, true)', 'distancePreference')
Require-Text 'src/main/java/cn/longer233/gamenarrator/vision/OllamaVisionClient.java' @(
    'EventWindowSecondPass.select', 'EVENT_SECOND_PASS', 'analyzeWithoutAi')
Require-Text 'src/test/java/cn/longer233/gamenarrator/vision/AdaptiveFrameSamplerTest.java' @('AdaptiveFrameSampler.sample')
Require-Text 'src/test/java/cn/longer233/gamenarrator/vision/EventWindowSecondPassTest.java' @('EventWindowSecondPass.select')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-204 | P0 |')

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) event-driven sampling violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: event-driven sampling covers scene/audio anchors, pre-center-post windows and conditional second pass'
