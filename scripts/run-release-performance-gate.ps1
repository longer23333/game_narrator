param(
    [Parameter(Mandatory=$true)][string]$WorkRoot,
    [Parameter(Mandatory=$true)][string]$GpuPythonPath,
    [Parameter(Mandatory=$true)][string]$FfmpegPath,
    [long]$MinimumFreeBytes = 50GB,
    [ValidateRange(1,1440)][int]$MinimumLongRunMinutes = 60
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$work=[IO.Path]::GetFullPath($WorkRoot)
$gpuPython=(Resolve-Path -LiteralPath $GpuPythonPath).Path
$ffmpeg=(Resolve-Path -LiteralPath $FfmpegPath).Path
New-Item -ItemType Directory -Path $work -Force | Out-Null
$drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($work))
if($drive.AvailableFreeSpace-lt$MinimumFreeBytes){throw "Target drive needs at least $MinimumFreeBytes free bytes"}
$reportRoot=Join-Path $root 'artifacts/release-performance-reports';New-Item -ItemType Directory -Path $reportRoot -Force|Out-Null
$results=[ordered]@{}
function Invoke-Scenario([string]$name,[string]$validation,[scriptblock]$action){
    $log=Join-Path $reportRoot "$name.log";$timer=[Diagnostics.Stopwatch]::StartNew()
    $relativeLog="artifacts/release-performance-reports/$name.log"
    try{&$action *>&1|Out-File -LiteralPath $log -Encoding utf8;$timer.Stop();$results[$name]=[ordered]@{status='passed';validation=$validation;elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3);reportFile=$relativeLog;reportSha256=(Get-FileHash $log -Algorithm SHA256).Hash}}
    catch{$timer.Stop();$_.Exception.Message|Out-File $log -Append -Encoding utf8;$results[$name]=[ordered]@{status='failed';validation=$validation;elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3);reportFile=$relativeLog;reportSha256=(Get-FileHash $log -Algorithm SHA256).Hash};throw}
}
function Invoke-MarkedScript([string]$script,[string[]]$arguments,[string]$marker){
    $output=& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script @arguments 2>&1;$exit=$LASTEXITCODE;$output
    if($exit-ne0){throw "scenario script exited ${exit}: $script"}
    if(-not (($output|Out-String).Contains($marker))){throw "scenario command did not emit required marker: $marker"}
}
function Assert-DiskGuard([long]$available,[long]$required){if($available-lt$required){throw "DISK_LOW_REJECTED available=$available required=$required"}}
$sparse=Join-Path $work 'sparse-40gb-input.bin'
Invoke-Scenario 'input40gb' 'exact-sparse-length' { $stream=[IO.File]::Open($sparse,[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.SetLength(40GB);if($stream.Length-ne40GB){throw '40GB sparse input length mismatch'};"length=$($stream.Length)"}finally{$stream.Dispose();Remove-Item -LiteralPath $sparse -Force -ErrorAction SilentlyContinue} }
Invoke-Scenario 'diskLow' 'synthetic-low-space-rejection' { $rejected=$false;try{Assert-DiskGuard ([Math]::Max(0,$MinimumFreeBytes-1)) $MinimumFreeBytes}catch{if($_.Exception.Message-notlike 'DISK_LOW_REJECTED*'){throw};$rejected=$true;$_.Exception.Message};if(-not$rejected){throw 'synthetic disk-low condition was accepted'} }
Invoke-Scenario 'ffmpegInterrupted' 'forced-nonzero-process-exit' { $p=Start-Process $ffmpeg -ArgumentList @('-hide_banner','-f','lavfi','-i','testsrc=size=1280x720:rate=30','-t','3600','-f','null','-') -PassThru -WindowStyle Hidden;Start-Sleep -Seconds 3;$p.Kill();$p.WaitForExit();if(-not$p.HasExited-or$p.ExitCode-eq0){throw "FFmpeg interruption was not observed exit=$($p.ExitCode)"};"FFMPEG_INTERRUPTED exit=$($p.ExitCode)" }
$gpuScript=Join-Path $root 'scripts/invoke-gpu-oom-recovery.ps1'
$soakScript=Join-Path $root 'scripts/invoke-release-soak.ps1'
$recoveryScript=Join-Path $root 'scripts/invoke-pipeline-recovery-performance.ps1'
Invoke-Scenario 'gpuOom' 'real-cuda-oom-and-post-oom-calculation' { Invoke-MarkedScript $gpuScript @('-PythonPath',$gpuPython,'-WorkRoot',$work) 'GN_GPU_OOM_RECOVERED=1' }
Invoke-Scenario 'longRun' 'real-ffmpeg-soak-minimum-duration' { $timer=[Diagnostics.Stopwatch]::StartNew();Invoke-MarkedScript $soakScript @('-FfmpegPath',$ffmpeg,'-MinimumMinutes',[string]$MinimumLongRunMinutes) 'GN_LONG_RUN_COMPLETED=1';$timer.Stop();if($timer.Elapsed.TotalMinutes-lt$MinimumLongRunMinutes){throw "long-run duration $([Math]::Round($timer.Elapsed.TotalMinutes,2))m is below ${MinimumLongRunMinutes}m"} }
Invoke-Scenario 'recovery' 'real-nine-stage-corruption-recovery' { Invoke-MarkedScript $recoveryScript @('-FfmpegPath',$ffmpeg) 'GN_RECOVERY_VERIFIED=1' }
$gpu=(nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader 2>$null|Select-Object -First 1)
$implementations=[ordered]@{}
$implementationFiles=[ordered]@{gpuOom=$gpuScript;longRun=$soakScript;recovery=$recoveryScript}
foreach($entry in $implementationFiles.GetEnumerator()){$implementations[$entry.Key]=(Get-FileHash -LiteralPath $entry.Value -Algorithm SHA256).Hash}
$evidence=[ordered]@{schemaVersion=3;commit=(git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim();completedAt=[DateTimeOffset]::Now.ToString('o');minimumLongRunMinutes=$MinimumLongRunMinutes;runnerSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash;implementations=$implementations;machine=[ordered]@{os=[Environment]::OSVersion.VersionString;gpu=([string]$gpu);processor=$env:PROCESSOR_IDENTIFIER};scenarios=$results}
$out=Join-Path $root 'artifacts/release-performance-gate.json';New-Item -ItemType Directory -Path (Split-Path $out) -Force|Out-Null;$evidence|ConvertTo-Json -Depth 6|Set-Content $out -Encoding UTF8
Write-Host "PASS: release performance evidence written to $out"
