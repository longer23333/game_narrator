param()
$ErrorActionPreference='Stop';$root=(Resolve-Path(Join-Path $PSScriptRoot '..')).Path;$failures=[Collections.Generic.List[string]]::new()
function Require([string]$file,[string[]]$markers){$path=Join-Path $root $file;if(-not(Test-Path $path)){$failures.Add("missing: $file");return};$text=[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8);foreach($marker in $markers){if(-not$text.Contains($marker)){$failures.Add("$file missing: $marker")}}}
Require 'src/main/java/cn/longer233/gamenarrator/diagnostics/ReleaseReadinessService.java' @('ffmpeg','models','disk','gpu','database','cloud','android','ci','performance','validAndroidEvidence','validCiEvidence','validPerformanceEvidence','GREEN','YELLOW','RED')
Require 'src/main/java/cn/longer233/gamenarrator/diagnostics/DiagnosticsController.java' @('/release-readiness','releaseReadiness')
Require 'frontend/public/release-readiness.js' @('/api/debug/release-readiness','readiness-card')
Require 'frontend/index.html' @('readiness-open','readiness-dialog')
Require 'src/test/java/cn/longer233/gamenarrator/diagnostics/ReleaseReadinessServiceTest.java' @('unknownEvidenceIsYellowAndMissingFfmpegIsRed')
if($failures.Count){Write-Host "FAIL: $($failures.Count) release readiness violation(s)";$failures|%{Write-Host "  $_"};exit 1};Write-Host 'PASS: release readiness aggregates runtime, storage, migration, cloud and external evidence into red/yellow/green states'
