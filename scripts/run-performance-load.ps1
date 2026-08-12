param(
    [string]$JMeter = 'jmeter', [string]$HostName = '127.0.0.1', [int]$Port = 8080,
    [int]$Users = 8, [int]$DurationSeconds = 60, [string]$VideoFile = '',
    [double]$MaximumErrorPercent = 1.0, [int]$MaximumP95Milliseconds = 5000
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$plan = Join-Path $root 'performance/jmeter/api-load.jmx'
$output = Join-Path $root 'target/performance'
New-Item -ItemType Directory -Force $output | Out-Null
$results = Join-Path $output 'api-load.jtl'
$report = Join-Path $output 'html'
if ($VideoFile -and -not (Test-Path -LiteralPath $VideoFile -PathType Leaf)) { throw "Video file does not exist: $VideoFile" }
$arguments = @('-n','-t',$plan,'-l',$results,'-e','-o',$report,"-Jhost=$HostName","-Jport=$Port","-Jusers=$Users","-JdurationSeconds=$DurationSeconds")
if ($VideoFile) { $arguments += '-JvideoFile=' + (Resolve-Path $VideoFile).Path }
& $JMeter @arguments
if ($LASTEXITCODE -ne 0) { throw "JMeter exited with code $LASTEXITCODE" }
$rows = Import-Csv $results
if (-not $rows) { throw 'JMeter produced no samples' }
$failures = @($rows | Where-Object { $_.success -ne 'true' }).Count
$errorPercent = 100.0 * $failures / $rows.Count
$elapsed = @($rows | ForEach-Object { [int]$_.elapsed } | Sort-Object)
$p95 = $elapsed[[Math]::Min($elapsed.Count - 1, [Math]::Ceiling($elapsed.Count * 0.95) - 1)]
$summary = [ordered]@{ samples=$rows.Count; failures=$failures; errorPercent=[Math]::Round($errorPercent,3); p95Milliseconds=$p95; users=$Users; durationSeconds=$DurationSeconds }
$summary | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $output 'summary.json')
if ($errorPercent -gt $MaximumErrorPercent) { throw "Error rate $errorPercent% exceeds $MaximumErrorPercent%" }
if ($p95 -gt $MaximumP95Milliseconds) { throw "P95 ${p95}ms exceeds ${MaximumP95Milliseconds}ms" }
$summary
