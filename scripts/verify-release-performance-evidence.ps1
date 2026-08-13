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
    if ($evidence.schemaVersion -ne 2) { $failures.Add('performance evidence schemaVersion must be 2') }
    if ([int]$evidence.minimumLongRunMinutes -lt 60) { $failures.Add('performance evidence long-run minimum must be at least 60 minutes') }
    $commit = (git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'unable to read current commit' }
    if ($evidence.commit -ne $commit) { $failures.Add("performance evidence commit mismatch: $($evidence.commit) != $commit") }
    try { $age = [DateTimeOffset]::Now - [DateTimeOffset]::Parse([string]$evidence.completedAt) }
    catch { $failures.Add('performance evidence completedAt is invalid'); $age = [TimeSpan]::MaxValue }
    if ($age.TotalHours -gt $MaximumAgeHours) { $failures.Add("performance evidence is older than $MaximumAgeHours hours") }
    $validations = @{input40gb='exact-sparse-length';diskLow='synthetic-low-space-rejection';gpuOom='explicit-oom-and-recovery-marker';ffmpegInterrupted='forced-nonzero-process-exit';longRun='minimum-duration-and-completion-marker';recovery='explicit-resume-integrity-marker'}
    foreach ($scenario in @('input40gb','diskLow','gpuOom','ffmpegInterrupted','longRun','recovery')) {
        $result = $evidence.scenarios.$scenario
        if ($null -eq $result -or $result.status -ne 'passed' -or [string]::IsNullOrWhiteSpace([string]$result.reportSha256)) {
            $failures.Add("scenario has no passed report hash: $scenario")
        }
        elseif ($result.validation -ne $validations[$scenario] -or [double]$result.elapsedSeconds -lt 0) {
            $failures.Add("scenario lacks required validation metadata: $scenario")
        }
    }
    if ([double]$evidence.scenarios.longRun.elapsedSeconds -lt 3600) { $failures.Add('long-run evidence is shorter than 3600 seconds') }
    if ([string]::IsNullOrWhiteSpace([string]$evidence.machine.gpu) -or [string]::IsNullOrWhiteSpace([string]$evidence.machine.os)) {
        $failures.Add('performance evidence lacks target machine OS/GPU identity')
    }
}
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) release performance evidence violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: fresh same-commit release performance evidence covers 40GB, disk low, GPU OOM, FFmpeg interruption, long run and recovery'
