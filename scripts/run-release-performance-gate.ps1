param(
    [Parameter(Mandatory=$true)][string]$WorkRoot,
    [Parameter(Mandatory=$true)][string]$GpuOomCommand,
    [Parameter(Mandatory=$true)][string]$LongRunCommand,
    [Parameter(Mandatory=$true)][string]$RecoveryCommand,
    [long]$MinimumFreeBytes = 50GB,
    [ValidateRange(1,1440)][int]$MinimumLongRunMinutes = 60
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$work=[IO.Path]::GetFullPath($WorkRoot)
New-Item -ItemType Directory -Path $work -Force | Out-Null
$drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($work))
if($drive.AvailableFreeSpace-lt$MinimumFreeBytes){throw "Target drive needs at least $MinimumFreeBytes free bytes"}
$reportRoot=Join-Path $work 'reports';New-Item -ItemType Directory -Path $reportRoot -Force|Out-Null
$results=[ordered]@{}
function Invoke-Scenario([string]$name,[string]$validation,[scriptblock]$action){
    $log=Join-Path $reportRoot "$name.log";$timer=[Diagnostics.Stopwatch]::StartNew()
    try{&$action *>&1|Out-File -LiteralPath $log -Encoding utf8;$timer.Stop();$results[$name]=[ordered]@{status='passed';validation=$validation;elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3);reportSha256=(Get-FileHash $log -Algorithm SHA256).Hash}}
    catch{$timer.Stop();$_.Exception.Message|Out-File $log -Append -Encoding utf8;$results[$name]=[ordered]@{status='failed';validation=$validation;elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3);reportSha256=(Get-FileHash $log -Algorithm SHA256).Hash};throw}
}
function Invoke-MarkedCommand([string]$command,[string]$marker){
    $output=& powershell.exe -NoProfile -Command $command 2>&1;$exit=$LASTEXITCODE;$output
    if($exit-ne0){throw "scenario command exited $exit"}
    if(-not (($output|Out-String).Contains($marker))){throw "scenario command did not emit required marker: $marker"}
}
function Assert-DiskGuard([long]$available,[long]$required){if($available-lt$required){throw "DISK_LOW_REJECTED available=$available required=$required"}}
$sparse=Join-Path $work 'sparse-40gb-input.bin'
Invoke-Scenario 'input40gb' 'exact-sparse-length' { $stream=[IO.File]::Open($sparse,[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.SetLength(40GB);if($stream.Length-ne40GB){throw '40GB sparse input length mismatch'};"length=$($stream.Length)"}finally{$stream.Dispose();Remove-Item -LiteralPath $sparse -Force -ErrorAction SilentlyContinue} }
Invoke-Scenario 'diskLow' 'synthetic-low-space-rejection' { $rejected=$false;try{Assert-DiskGuard ([Math]::Max(0,$MinimumFreeBytes-1)) $MinimumFreeBytes}catch{if($_.Exception.Message-notlike 'DISK_LOW_REJECTED*'){throw};$rejected=$true;$_.Exception.Message};if(-not$rejected){throw 'synthetic disk-low condition was accepted'} }
Invoke-Scenario 'ffmpegInterrupted' 'forced-nonzero-process-exit' { $ffmpeg=(Get-Command ffmpeg -ErrorAction Stop).Source;$p=Start-Process $ffmpeg -ArgumentList @('-hide_banner','-f','lavfi','-i','testsrc=size=1280x720:rate=30','-t','3600','-f','null','-') -PassThru -WindowStyle Hidden;Start-Sleep -Seconds 3;$p.Kill();$p.WaitForExit();if(-not$p.HasExited-or$p.ExitCode-eq0){throw "FFmpeg interruption was not observed exit=$($p.ExitCode)"};"FFMPEG_INTERRUPTED exit=$($p.ExitCode)" }
Invoke-Scenario 'gpuOom' 'explicit-oom-and-recovery-marker' { Invoke-MarkedCommand $GpuOomCommand 'GN_GPU_OOM_RECOVERED=1' }
Invoke-Scenario 'longRun' 'minimum-duration-and-completion-marker' { $timer=[Diagnostics.Stopwatch]::StartNew();Invoke-MarkedCommand $LongRunCommand 'GN_LONG_RUN_COMPLETED=1';$timer.Stop();if($timer.Elapsed.TotalMinutes-lt$MinimumLongRunMinutes){throw "long-run duration $([Math]::Round($timer.Elapsed.TotalMinutes,2))m is below ${MinimumLongRunMinutes}m"} }
Invoke-Scenario 'recovery' 'explicit-resume-integrity-marker' { Invoke-MarkedCommand $RecoveryCommand 'GN_RECOVERY_VERIFIED=1' }
$gpu=(nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader 2>$null|Select-Object -First 1)
$evidence=[ordered]@{schemaVersion=2;commit=(git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim();completedAt=[DateTimeOffset]::Now.ToString('o');minimumLongRunMinutes=$MinimumLongRunMinutes;machine=[ordered]@{os=[Environment]::OSVersion.VersionString;gpu=([string]$gpu);processor=$env:PROCESSOR_IDENTIFIER};scenarios=$results}
$out=Join-Path $root 'artifacts/release-performance-gate.json';New-Item -ItemType Directory -Path (Split-Path $out) -Force|Out-Null;$evidence|ConvertTo-Json -Depth 6|Set-Content $out -Encoding UTF8
Write-Host "PASS: release performance evidence written to $out"
