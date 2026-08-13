param(
    [string]$EvidenceFile = 'artifacts/release-performance-gate.json',
    [int]$MaximumAgeHours = 168
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$path = Join-Path $root $EvidenceFile
$failures = [Collections.Generic.List[string]]::new()
if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $failures.Add("missing performance evidence: $EvidenceFile") }
else {
    $evidence = [IO.File]::ReadAllText($path,[Text.Encoding]::UTF8) | ConvertFrom-Json
    $commit = (git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'unable to read current commit' }
    if ($evidence.commit -ne $commit) { $failures.Add("performance evidence commit mismatch: $($evidence.commit) != $commit") }
    try { $age = [DateTimeOffset]::Now - [DateTimeOffset]::Parse([string]$evidence.completedAt) }
    catch { $failures.Add('performance evidence completedAt is invalid'); $age = [TimeSpan]::MaxValue }
    if ($age.TotalHours -gt $MaximumAgeHours) { $failures.Add("performance evidence is older than $MaximumAgeHours hours") }
    foreach ($scenario in @('input40gb','diskLow','gpuOom','ffmpegInterrupted','longRun','recovery')) {
        $result = $evidence.scenarios.$scenario
        if ($null -eq $result -or $result.status -ne 'passed' -or [string]::IsNullOrWhiteSpace([string]$result.reportSha256)) {
            $failures.Add("scenario has no passed report hash: $scenario")
        }
    }
    if ([string]::IsNullOrWhiteSpace([string]$evidence.machine.gpu) -or [string]::IsNullOrWhiteSpace([string]$evidence.machine.os)) {
        $failures.Add('performance evidence lacks target machine OS/GPU identity')
    }
}
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) release performance evidence violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: fresh same-commit release performance evidence covers 40GB, disk low, GPU OOM, FFmpeg interruption, long run and recovery'
