param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()

function Require([string]$file, [string[]]$markers) {
    $path = Join-Path $root $file
    if (-not (Test-Path $path)) { $failures.Add("missing: $file"); return }
    $content = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($marker in $markers) {
        if (-not $content.Contains($marker)) { $failures.Add("$file missing: $marker") }
    }
}

Require 'frontend/index.html' @(
    'class="quick-start"', 'data-quick-action="create"',
    'class="form-intro"', 'class="primary-submit"'
)
Require 'frontend/src/components/TaskCard.vue' @(
    'data-open-list-task', 'task-card-open',
    'data-rename-list-task', 'data-delete-list-task'
)
Require 'frontend/public/app.js' @(
    'class="detail-block task-action-center"', 'class="task-primary-actions"',
    'class="danger-button"', 'class="task-more-actions"', 'class="text-danger-button"'
)
Require 'frontend/public/ui-refresh.css' @(
    '.quick-start{', '.task-action-center{', '.danger-button{',
    '@media(max-width:650px){.quick-start{grid-template-columns:1fr}'
)

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) UI action clarity violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: primary workflows, next actions, secondary management and mobile layout are explicit'
