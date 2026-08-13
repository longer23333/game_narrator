param(
    [Parameter(Mandatory=$true)][string]$WorkRoot,
    [Parameter(Mandatory=$true)][string]$GpuOomCommand,
    [Parameter(Mandatory=$true)][string]$LongRunCommand,
    [Parameter(Mandatory=$true)][string]$RecoveryCommand,
    [long]$MinimumFreeBytes = 50GB
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$work=[IO.Path]::GetFullPath($WorkRoot)
New-Item -ItemType Directory -Path $work -Force | Out-Null
$drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($work))
if($drive.AvailableFreeSpace-lt$MinimumFreeBytes){throw "Target drive needs at least $MinimumFreeBytes free bytes"}
$reportRoot=Join-Path $work 'reports';New-Item -ItemType Directory -Path $reportRoot -Force|Out-Null
$results=[ordered]@{}
function Invoke-Scenario([string]$name,[scriptblock]$action){$log=Join-Path $reportRoot "$name.log";try{&$action *>&1|Out-File -LiteralPath $log -Encoding utf8;$results[$name]=[ordered]@{status='passed';reportSha256=(Get-FileHash $log -Algorithm SHA256).Hash}}catch{$_.Exception.Message|Out-File $log -Append -Encoding utf8;$results[$name]=[ordered]@{status='failed';reportSha256=(Get-FileHash $log -Algorithm SHA256).Hash};throw}}
$sparse=Join-Path $work 'sparse-40gb-input.bin'
Invoke-Scenario 'input40gb' { $stream=[IO.File]::Open($sparse,[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.SetLength(40GB);if($stream.Length-ne40GB){throw '40GB sparse input length mismatch'};"length=$($stream.Length)"}finally{$stream.Dispose()} }
Invoke-Scenario 'diskLow' { "available=$($drive.AvailableFreeSpace) minimum=$MinimumFreeBytes";if($drive.AvailableFreeSpace-lt$MinimumFreeBytes){throw 'disk free-space guard did not have the required headroom'} }
Invoke-Scenario 'ffmpegInterrupted' { $ffmpeg=(Get-Command ffmpeg -ErrorAction Stop).Source;$p=Start-Process $ffmpeg -ArgumentList @('-hide_banner','-f','lavfi','-i','testsrc=size=1280x720:rate=30','-t','3600','-f','null','-') -PassThru -WindowStyle Hidden;Start-Sleep -Seconds 3;$p.Kill($true);$p.WaitForExit();"interrupted=$($p.HasExited) exit=$($p.ExitCode)" }
Invoke-Scenario 'gpuOom' { & powershell.exe -NoProfile -Command $GpuOomCommand;if($LASTEXITCODE-ne0){throw "GPU OOM scenario exited $LASTEXITCODE"} }
Invoke-Scenario 'longRun' { & powershell.exe -NoProfile -Command $LongRunCommand;if($LASTEXITCODE-ne0){throw "long-run scenario exited $LASTEXITCODE"} }
Invoke-Scenario 'recovery' { & powershell.exe -NoProfile -Command $RecoveryCommand;if($LASTEXITCODE-ne0){throw "recovery scenario exited $LASTEXITCODE"} }
$gpu=(nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader 2>$null|Select-Object -First 1)
$evidence=[ordered]@{schemaVersion=1;commit=(git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim();completedAt=[DateTimeOffset]::Now.ToString('o');machine=[ordered]@{os=[Environment]::OSVersion.VersionString;gpu=([string]$gpu);processor=$env:PROCESSOR_IDENTIFIER};scenarios=$results}
$out=Join-Path $root 'artifacts/release-performance-gate.json';New-Item -ItemType Directory -Path (Split-Path $out) -Force|Out-Null;$evidence|ConvertTo-Json -Depth 6|Set-Content $out -Encoding UTF8
Write-Host "PASS: release performance evidence written to $out"
