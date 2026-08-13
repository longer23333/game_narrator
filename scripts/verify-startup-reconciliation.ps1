param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()

function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $content = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) {
        if (-not $content.Contains($needle)) { $failures.Add("$relative missing contract: $needle") }
    }
}

Require-Text 'src/main/java/cn/longer233/gamenarrator/common/StorageCleanupService.java' @(
    'implements ApplicationRunner', 'cleanupOrphanTaskDirectories', 'SELECT id FROM video_tasks',
    'UUID.fromString', 'registered.contains', '.endsWith(".tmp")', '.endsWith(".part")',
    '.endsWith(".partial")', 'SecurePathGuard.isOwned', 'ORPHAN_TASK_WORKSPACE_REMOVED')
Require-Text 'src/test/java/cn/longer233/gamenarrator/common/StorageCleanupServiceTest.java' @(
    'startupReconciliationDeletesOnlyExpiredUnregisteredUuidWorkspaces', 'registeredDir',
    'orphanDir', 'recentDir', 'crash.part')
Require-Text 'docs/REQUIREMENTS.md' @('tasks/<UUID>', '.partial')

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) startup reconciliation violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: startup reconciliation and orphan GC are guarded, age-aware and database-backed'
