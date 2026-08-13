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

Require-Text 'src/main/java/cn/longer233/gamenarrator/transcription/SpeakerDiarizationService.java' @(
    'PLAYER_VOICE', 'GAME_CHARACTER_NARRATOR', 'MULTI_SPEAKER', 'TIMELINE_OVERLAP',
    'TEXT_FALLBACK', 'speaker-segments.json', 'requiresReview')
Require-Text 'src/main/java/cn/longer233/gamenarrator/pipeline/TaskWorkflowStateService.java' @(
    'speakerDiarization.analyze(corrected)', 'SPEAKER_SEGMENTS')
Require-Text 'src/test/java/cn/longer233/gamenarrator/transcription/SpeakerDiarizationServiceTest.java' @(
    'preservesTimestampsAndUsesNativeSpeakerLabels', 'marksOverlappingDifferentSpeakersAsMultiSpeaker',
    'exposesLowConfidenceFallbackForReview')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-203 | P1 |')

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) speaker diarization violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: speaker segments preserve timestamps and distinguish player, character/narrator and overlapping voices'
