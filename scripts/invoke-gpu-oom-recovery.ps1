param(
    [Parameter(Mandatory=$true)][string]$PythonPath,
    [Parameter(Mandatory=$true)][string]$WorkRoot
)
$ErrorActionPreference = 'Stop'
$python = (Resolve-Path -LiteralPath $PythonPath).Path
$work = [IO.Path]::GetFullPath($WorkRoot)
New-Item -ItemType Directory -Path $work -Force | Out-Null
$probe = Join-Path $work 'gpu-oom-recovery-probe.py'
$source = @'
import gc
import json
import torch

if not torch.cuda.is_available():
    raise RuntimeError("CUDA is unavailable")

device = torch.device("cuda:0")
before_free, total = torch.cuda.mem_get_info(device)
allocations = []
oom_observed = False
try:
    while True:
        allocations.append(torch.empty((64 * 1024 * 1024,), dtype=torch.float32, device=device))
except torch.OutOfMemoryError:
    oom_observed = True
finally:
    allocations.clear()
    gc.collect()
    torch.cuda.empty_cache()
    torch.cuda.synchronize(device)

if not oom_observed:
    raise RuntimeError("CUDA OOM was not observed")
after_oom_free, _ = torch.cuda.mem_get_info(device)
recovery = torch.ones((1024, 1024), dtype=torch.float32, device=device)
if float(recovery.sum().item()) != 1048576.0:
    raise RuntimeError("post-OOM CUDA calculation failed")
del recovery
torch.cuda.empty_cache()
torch.cuda.synchronize(device)
after_recovery_free, _ = torch.cuda.mem_get_info(device)
print(json.dumps({
    "device": torch.cuda.get_device_name(device),
    "totalBytes": total,
    "beforeFreeBytes": before_free,
    "afterOomFreeBytes": after_oom_free,
    "afterRecoveryFreeBytes": after_recovery_free,
    "oomObserved": oom_observed,
    "postOomCalculation": "passed"
}))
print("GN_GPU_OOM_RECOVERED=1")
'@
try {
    [IO.File]::WriteAllText($probe, $source, [Text.UTF8Encoding]::new($false))
    & $python $probe
    if ($LASTEXITCODE -ne 0) { throw "GPU OOM recovery probe exited $LASTEXITCODE" }
} finally {
    Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
}
