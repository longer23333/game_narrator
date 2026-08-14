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
    if ($evidence.schemaVersion -ne 3) { $failures.Add('performance evidence schemaVersion must be 3') }
    if ([int]$evidence.minimumLongRunMinutes -lt 60) { $failures.Add('performance evidence long-run minimum must be at least 60 minutes') }
    $commit = (git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'unable to read current commit' }
    if ($evidence.commit -ne $commit) { $failures.Add("performance evidence commit mismatch: $($evidence.commit) != $commit") }
    try { $age = [DateTimeOffset]::Now - [DateTimeOffset]::Parse([string]$evidence.completedAt) }
    catch { $failures.Add('performance evidence completedAt is invalid'); $age = [TimeSpan]::MaxValue }
    if ($age.TotalHours -gt $MaximumAgeHours) { $failures.Add("performance evidence is older than $MaximumAgeHours hours") }
    $runner = Join-Path $root 'scripts/run-release-performance-gate.ps1'
    if ($evidence.runnerSha256 -ne (Get-FileHash -LiteralPath $runner -Algorithm SHA256).Hash) { $failures.Add('performance runner hash mismatch') }
    $implementations = @{gpuOom='scripts/invoke-gpu-oom-recovery.ps1';longRun='scripts/invoke-release-soak.ps1';recovery='scripts/invoke-pipeline-recovery-performance.ps1'}
    foreach ($entry in $implementations.GetEnumerator()) {
        $implementationPath = Join-Path $root $entry.Value
        if ($evidence.implementations.($entry.Key) -ne (Get-FileHash -LiteralPath $implementationPath -Algorithm SHA256).Hash) {
            $failures.Add("scenario implementation hash mismatch: $($entry.Key)")
        }
    }
    $validations = @{input40gb='exact-sparse-length';diskLow='synthetic-low-space-rejection';gpuOom='real-cuda-oom-and-post-oom-calculation';ffmpegInterrupted='forced-nonzero-process-exit';longRun='real-ffmpeg-soak-minimum-duration';recovery='real-nine-stage-corruption-recovery'}
    foreach ($scenario in @('input40gb','diskLow','gpuOom','ffmpegInterrupted','longRun','recovery')) {
        $result = $evidence.scenarios.$scenario
        if ($null -eq $result -or $result.status -ne 'passed' -or [string]::IsNullOrWhiteSpace([string]$result.reportSha256)) {
            $failures.Add("scenario has no passed report hash: $scenario")
        }
        elseif ($result.validation -ne $validations[$scenario] -or [double]$result.elapsedSeconds -lt 0) {
            $failures.Add("scenario lacks required validation metadata: $scenario")
        }
        else {
            $reportPath = Join-Path $root ([string]$result.reportFile)
            if ([string]$result.reportFile -ne "artifacts/release-performance-reports/$scenario.log" -or
                    -not (Test-Path -LiteralPath $reportPath -PathType Leaf) -or
                    (Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash -ne $result.reportSha256) {
                $failures.Add("scenario report file or hash mismatch: $scenario")
            }
        }
    }
    if ([double]$evidence.scenarios.longRun.elapsedSeconds -lt 3600) { $failures.Add('long-run evidence is shorter than 3600 seconds') }
    if ([string]::IsNullOrWhiteSpace([string]$evidence.machine.gpu) -or [string]::IsNullOrWhiteSpace([string]$evidence.machine.os)) {
        $failures.Add('performance evidence lacks target machine OS/GPU identity')
    }
}
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) release performance evidence violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: fresh same-commit release performance evidence contains hashed reports for 40GB, disk low, real CUDA OOM recovery, FFmpeg interruption, 60-minute soak and pipeline recovery'
