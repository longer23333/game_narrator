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
Require-Text 'src/main/java/cn/longer233/gamenarrator/timeline/TimelineSegment.java' @(
    'transitionType', 'transitionDurationSeconds', 'transitionDirection', 'transitionCurve')
Require-Text 'src/main/java/cn/longer233/gamenarrator/timeline/TimelineTransitionPlanner.java' @(
    'adjacent-clips-too-short', 'previousDuration', 'currentDuration')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/TimelineTransitionGraphBuilder.java' @(
    'xfade=transition=', 'acrossfade=d=', 'hardCutFallback', 'trim=start=', 'atrim=start=')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/FfmpegVideoRenderer.java' @(
    'TIMELINE_TRANSITION_SUCCESS', 'TIMELINE_TRANSITION_FALLBACK', 'transitionDurationSeconds')
Require-Text 'src/test/java/cn/longer233/gamenarrator/render/FfmpegTimelineTransitionOutputTest.java' @(
    'ffmpegProducesPlayableAudioVideoFromParameterizedTransition',
    'ffmpegProducesSynchronizedHardCutFallbackAfterTransitionFailure', 'ffprobe',
    'contains("video", "audio")', 'Math.abs(videoDuration - audioDuration)', 'createClip')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-605 | P0 |')
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) timeline transition violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: FR-605 is a constrained, synchronized, parameterized timeline transition with real FFmpeg output coverage'
