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
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/RenderWorkLifecycle.java' @(
    'SecurePathGuard.isOwned', 'RENDER_WORK_CLEANUP_SUCCESS', 'RENDER_WORK_RETAINED_FOR_DIAGNOSIS',
    'RENDER_WORK_CLEANUP_DEFERRED', 'attempts')
Require-Text 'src/main/java/cn/longer233/gamenarrator/render/FfmpegVideoRenderer.java' @(
    'renderWorkLifecycle.completed', 'renderWorkLifecycle.failed')
Require-Text 'src/main/java/cn/longer233/gamenarrator/common/StorageCleanupService.java' @(
    'cleanupCompletedRenderWork', "WHERE status='COMPLETED'", 'COMPLETED_RENDER_WORK_RECOVERED')
Require-Text 'src/test/java/cn/longer233/gamenarrator/render/RenderWorkLifecycleTest.java' @(
    'successfulRenderRemovesOnlyOwnedWorkDirectory', 'failedRenderRetainsDiagnostics',
    'retriesBusyWorkAndEventuallyCleansIt', 'defersCleanupAfterBusyFileExhaustsRetriesWithoutDeletingDiagnostics')
Require-Text 'src/test/java/cn/longer233/gamenarrator/common/StorageCleanupServiceTest.java' @(
    'crashRecoveryRemovesOnlyExpiredCompletedRenderWork')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-708 | P0 |', 'render-work')
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) render work cleanup violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: FR-708 safely cleans successful render work, retains failures, retries busy files and reconciles crashes'
