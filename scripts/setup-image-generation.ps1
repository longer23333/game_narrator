param([string]$Endpoint = "http://127.0.0.1:8188", [string]$ComfyUiPath = $env:COMFYUI_PATH, [string]$ModelName = "ChenkinNoob-XL-V0.5.safetensors")
$ErrorActionPreference = 'Stop'
Write-Host "检查本地图片生成环境..."
if ($ComfyUiPath -and (Test-Path (Join-Path $ComfyUiPath 'main.py'))) {
  $python = Join-Path (Split-Path $ComfyUiPath -Parent) 'python\python.exe'
  if (-not (Test-Path $python)) { throw "找到了 ComfyUI，但缺少 Python：$python" }
  $port = ([uri]$Endpoint).Port
  Start-Process -FilePath $python -ArgumentList '-s', (Join-Path $ComfyUiPath 'main.py'), '--windows-standalone-build', '--disable-auto-launch', '--listen', '127.0.0.1', '--port', $port -WorkingDirectory $ComfyUiPath -WindowStyle Hidden
  Write-Host "已启动本地 ComfyUI：$Endpoint"
  Write-Host "请确认模型位于 $ComfyUiPath\models\checkpoints\$ModelName"
  exit 0
}
try { docker info | Out-Null } catch { throw "未找到可用 ComfyUI。请设置 COMFYUI_PATH，或启动 Docker Desktop。" }
$images = @('ghcr.io/ai-dock/comfyui:latest','yanwk/comfyui-boot:latest')
foreach ($image in $images) {
  try { docker pull $image; if ($LASTEXITCODE -eq 0) { $selected = $image; break } } catch { Write-Warning $_ }
}
if (-not $selected) { throw '无法从当前镜像源下载 ComfyUI。请修复 Docker registry mirror 后重新运行。' }
docker rm -f game-narrator-comfyui 2>$null
docker run -d --name game-narrator-comfyui --gpus all -p $(([uri]$Endpoint).Port):8188 -v "${PWD}\storage\image-generation:/workspace" $selected
Write-Host "图片引擎已启动：$Endpoint"
Write-Host "请在 ComfyUI 中安装 SD 1.5 Turbo，并将 IMAGE_GENERATION_ENDPOINT=$Endpoint 写入环境变量。"
