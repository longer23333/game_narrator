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
