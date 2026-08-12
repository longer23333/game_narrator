param()
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidRoot = Join-Path $projectRoot "android-app"
$main = Join-Path $androidRoot "app\src\main"
$test = Join-Path $androidRoot "app\src\test"
$failures = [Collections.Generic.List[string]]::new()

function Test-ForbiddenClaims([string]$Root, [string]$Label) {
    $patterns = @(
        'AI 已可用',
        '字幕烧录完成',
        '自动混音完成',
        '平台下载完成',
        '端侧转写完成'
    )
    foreach ($pattern in $patterns) {
        $hits = Get-ChildItem -LiteralPath $Root -Recurse -File |
            Where-Object { $_.Extension -in @(".java", ".kt", ".xml", ".txt", ".md") } |
            Select-String -SimpleMatch -Pattern $pattern
        foreach ($hit in $hits) {
            $failures.Add("$Label forbidden claim: $($hit.Path):$($hit.LineNumber) -> $pattern")
        }
    }
}

function Test-ForbiddenCloudCredentials([string]$Root, [string]$Label) {
    $patterns = @(
        'client_secret\s*[=:]\s*["''][^"'']+',
        'api[_-]?key\s*[=:]\s*["''][^"'']+',
        'password\s*[=:]\s*["''][^"'']+',
        'access_token\s*[=:]\s*["''][^"'']+'
    )
    $files = Get-ChildItem -LiteralPath $Root -Recurse -File |
        Where-Object { $_.Extension -in @(".java", ".kt", ".xml", ".properties") }
    foreach ($file in $files) {
        $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) {
                $failures.Add("$Label hard-coded credential pattern: $($file.FullName) -> $pattern")
            }
        }
    }
}

function Test-HistoryLimit([string]$Root, [string]$Label) {
    $history = Join-Path $Root "java\cn\longer233\gamenarrator\mobile\CommandHistory.java"
    if (-not (Test-Path -LiteralPath $history)) {
        $failures.Add("$Label CommandHistory.java not found")
        return
    }
    $text = [IO.File]::ReadAllText($history, [Text.Encoding]::UTF8)
    if ($text -notmatch 'DEFAULT_LIMIT\s*=\s*50') {
        $failures.Add("$Label CommandHistory.DEFAULT_LIMIT is not 50")
    }
}

Test-ForbiddenClaims $main "main"
Test-ForbiddenClaims $test "test"
Test-ForbiddenCloudCredentials $main "main"
Test-HistoryLimit $main "main"

if ($failures.Count -gt 0) {
    Write-Host "FAIL: $($failures.Count) guardrail violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "PASS: no forbidden AI claims, no cloud/credential patterns, history limit is 50"
