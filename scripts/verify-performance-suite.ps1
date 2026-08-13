$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
[xml]$plan = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'performance/jmeter/api-load.jmx')
$names = @($plan.SelectNodes('//HTTPSamplerProxy') | ForEach-Object { $_.testname })
foreach ($required in @('GET task list','GET export presets','POST create task')) {
    if ($names -notcontains $required) { throw "JMeter plan is missing: $required" }
}
$longTest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'src/test/java/cn/longer233/gamenarrator/performance/LongVideoPerformanceIT.java')
foreach ($marker in @('duration=300','1920x1080','Duration.ofMinutes(10)','maximumHeapGrowthBytes')) {
    if (-not $longTest.Contains($marker)) { throw "Long-video gate is missing marker: $marker" }
}
Write-Output 'PASS: concurrent API load plan and 300-second 1080p performance gate are present'
$releaseRunner = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'scripts/run-release-performance-gate.ps1')
$releaseVerifier = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'scripts/verify-release-performance-evidence.ps1')
foreach ($marker in @('input40gb','diskLow','gpuOom','ffmpegInterrupted','longRun','recovery','SetLength(40GB)',
        'GN_GPU_OOM_RECOVERED=1','GN_LONG_RUN_COMPLETED=1','GN_RECOVERY_VERIFIED=1','MinimumLongRunMinutes',
        'synthetic-low-space-rejection','forced-nonzero-process-exit','Remove-Item -LiteralPath $sparse')) {
    if (-not $releaseRunner.Contains($marker)) { throw "Release performance runner is missing: $marker" }
}
foreach ($marker in @('MaximumAgeHours','commit mismatch','reportSha256','schemaVersion must be 2','3600 seconds')) {
    if (-not $releaseVerifier.Contains($marker)) { throw "Release performance verifier is missing: $marker" }
}
Write-Output 'PASS: release target-machine gate covers 40GB, disk, GPU OOM, FFmpeg interruption, long run and recovery evidence'
