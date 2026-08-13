param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()

function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) {
        if (-not $text.Contains($needle)) { $failures.Add("$relative missing contract: $needle") }
    }
}

Require-Text 'src/main/java/cn/longer233/gamenarrator/ai/LocalModelCatalogService.java' @(
    'api/tags', 'api/pull', 'memoryMb', 'vramMb', 'validateName')
Require-Text 'src/main/java/cn/longer233/gamenarrator/ai/AiSettingsController.java' @(
    '/models', '/models/download', '/models/switch', '.installed().stream()', 'request.name()')
Require-Text 'src/main/java/cn/longer233/gamenarrator/ai/AdaptiveAiChatClient.java' @(
    'localModel(value, vision)', 'value.visionModel()', 'value.textModel()')
Require-Text 'frontend/index.html' @('local-model-manager', 'data-installed-models', 'data-recommended-models')
Require-Text 'frontend/public/app.js' @('loadLocalModels', 'data-model-action="download"', 'data-model-action="switch"')
Require-Text 'docs/REQUIREMENTS.md' @('| FR-205 | P2 |')

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) local model management violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: FR-205 covers installed catalog, resource estimates, health, download and runtime switching'
