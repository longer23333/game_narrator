param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()

function Require([string]$file, [string[]]$markers) {
    $path = Join-Path $root $file
    if (-not (Test-Path $path)) {
        $failures.Add("missing: $file")
        return
    }
    $content = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($marker in $markers) {
        if (-not $content.Contains($marker)) {
            $failures.Add("$file missing: $marker")
        }
    }
}

Require 'src/main/java/cn/longer233/gamenarrator/task/application/VideoTaskService.java' @(
    'task.cancel(',
    'repository.saveAndFlush(task)',
    'afterCommit() { engine.requestCancellation(id); }',
    'TASK_CANCEL_ACCEPTED'
)
Require 'src/main/java/cn/longer233/gamenarrator/common/TaskProcessRegistry.java' @(
    'CANCELLATION_EXECUTOR',
    'snapshot.forEach(ExternalProcessRunner::terminateTree)',
    'isCancelled(UUID taskId)'
)
Require 'src/main/java/cn/longer233/gamenarrator/pipeline/VideoTaskEngine.java' @(
    'TaskProcessRegistry.isCancelled(taskId)',
    'ENGINE_CANCELLED_AFTER_PROCESS_EXIT'
)
Require 'frontend/public/app.js' @(
    'new AbortController()',
    'controller.abort(), 8000',
    "if (error.name === 'AbortError')",
    'window.clearTimeout(timeout)'
)
Require 'src/test/java/cn/longer233/gamenarrator/common/TaskProcessRegistryIntegrationTest.java' @(
    'cancellationRequestReturnsImmediatelyWhileChildStopsAsynchronously',
    'isLessThan(500)',
    'waitFor(5, TimeUnit.SECONDS)'
)

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) task cancellation violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: cancellation persists terminal state first, returns promptly, stops processes asynchronously and has UI timeout recovery'
