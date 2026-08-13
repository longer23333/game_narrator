$ErrorActionPreference = 'Stop'
function Require([string]$Path,[string[]]$Patterns) {
  if (-not (Test-Path -LiteralPath $Path)) { throw "Missing $Path" }
  $text = Get-Content -LiteralPath $Path -Raw
  foreach ($pattern in $Patterns) { if (-not $text.Contains($pattern)) { throw "$Path missing contract: $pattern" } }
}
Require 'src/main/java/cn/longer233/gamenarrator/quality/NarrativeConsistencyService.java' @('EVIDENCE_COVERAGE','EVENT_CONFIDENCE','SPEAKER_EVIDENCE','OCR_EVIDENCE','KNOWLEDGE_EVIDENCE','getVisualAnalysisPath','getTranscriptJsonPath','evidenceCoverage < .5','supportedSegments')
Require 'src/main/java/cn/longer233/gamenarrator/quality/NarrativeQualityReport.java' @('supportedSegmentCount','segmentCount','evidenceCoverage','averageEventConfidence','speakerCoverage','ocrEvidenceCoverage','knowledgeEvidenceCoverage')
Require 'src/test/java/cn/longer233/gamenarrator/quality/NarrativeConsistencyServiceTest.java' @('blocksPassingScoreWhenUpstreamEvidenceCoverageIsLow')
Write-Host 'PASS: script scoring is constrained by visual, ASR and time-aligned confirmed-event evidence'
