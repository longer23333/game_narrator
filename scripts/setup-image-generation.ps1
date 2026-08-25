param([string]$Endpoint = "http://127.0.0.1:7860")
$ErrorActionPreference = 'Stop'
Write-Host "检查 RTX 4060 / Docker 与图片引擎部署状态..."
nvidia-smi
docker info | Out-Null
$images = @('ghcr.io/ai-dock/comfyui:latest','yanwk/comfyui-boot:latest')
foreach ($image in $images) {
  try { docker pull $image; if ($LASTEXITCODE -eq 0) { $selected = $image; break } } catch { Write-Warning $_ }
}
if (-not $selected) { throw '无法从当前镜像源下载 ComfyUI。请修复 Docker registry mirror 后重新运行。' }
docker rm -f game-narrator-comfyui 2>$null
docker run -d --name game-narrator-comfyui --gpus all -p 7860:8188 -v "${PWD}\storage\image-generation:/workspace" $selected
Write-Host "图片引擎已启动：$Endpoint"
Write-Host "请在 ComfyUI 中安装 SD 1.5 Turbo，并将 IMAGE_GENERATION_ENDPOINT=$Endpoint 写入环境变量。"
