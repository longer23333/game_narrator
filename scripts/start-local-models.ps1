[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot '..\data\config\local-models.json'),
    [switch]$SkipImages
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json

function Assert-DataDrive([string]$Path) {
    if (-not [IO.Path]::IsPathRooted($Path) -or
        [IO.Path]::GetPathRoot([IO.Path]::GetFullPath($Path)) -eq ($env:SystemDrive + '\')) {
        throw "模型与缓存必须放在非系统盘：$Path"
    }
}
foreach ($path in @($config.ollamaModels, $config.cacheRoot, $config.comfyUiPath)) { Assert-DataDrive $path }
$cacheRoot = $config.cacheRoot
foreach ($directory in @('temp', 'huggingface', 'torch', 'logs', 'comfy-user', 'comfy-output', 'comfy-input')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $cacheRoot $directory) | Out-Null
}
$env:OLLAMA_MODELS = $config.ollamaModels
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_BASE_URL = 'http://127.0.0.1:11434'
$env:OLLAMA_NUM_PARALLEL = '1'
$env:OLLAMA_MAX_LOADED_MODELS = '1'
$env:OLLAMA_KEEP_ALIVE = '2m'
$env:TEMP = Join-Path $cacheRoot 'temp'
$env:TMP = $env:TEMP
$env:HF_HOME = Join-Path $cacheRoot 'huggingface'
$env:HF_HUB_CACHE = Join-Path $env:HF_HOME 'hub'
$env:HF_XET_CACHE = Join-Path $env:HF_HOME 'xet'
$env:TORCH_HOME = Join-Path $cacheRoot 'torch'
$env:XDG_CACHE_HOME = $cacheRoot
$env:COMFYUI_PATH = $config.comfyUiPath
$env:IMAGE_GENERATION_ENDPOINT = 'http://127.0.0.1:8188'
$env:IMAGE_GENERATION_MODEL = $config.imageModel

function Test-Service([string]$Url) {
    try { Invoke-RestMethod -Uri $Url -TimeoutSec 3 | Out-Null; return $true }
    catch { return $false }
}
function Wait-Service([string]$Url, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while (-not (Test-Service $Url)) {
        if ((Get-Date) -ge $deadline) { throw "模型服务启动超时：$Url；请查看 $cacheRoot\logs" }
        Start-Sleep -Milliseconds 700
    }
}
if (-not (Test-Service 'http://127.0.0.1:11434/api/tags')) {
    if (-not (Test-Path -LiteralPath $config.ollamaExecutable)) { throw '找不到 Ollama 程序' }
    Start-Process -FilePath $config.ollamaExecutable -ArgumentList 'serve' -WindowStyle Hidden `
        -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $cacheRoot 'logs/ollama.out.log') `
        -RedirectStandardError (Join-Path $cacheRoot 'logs/ollama.err.log') | Out-Null
    Wait-Service 'http://127.0.0.1:11434/api/tags' 45
}
Write-Host "Ollama 已就绪，模型目录：$env:OLLAMA_MODELS"
if (-not $SkipImages -and -not (Test-Service 'http://127.0.0.1:8188/system_stats')) {
    $checkpoint = Join-Path $config.comfyUiPath ('models/checkpoints/' + $config.imageModel)
    if (-not (Test-Path -LiteralPath $checkpoint)) { throw "缺少图片模型：$checkpoint" }
    $arguments = @('-s', ('"' + (Join-Path $config.comfyUiPath 'main.py') + '"'),
        '--windows-standalone-build', '--disable-auto-launch', '--disable-all-custom-nodes',
        '--listen', '127.0.0.1', '--port', '8188',
        '--temp-directory', ('"' + $env:TEMP + '"'),
        '--user-directory', ('"' + (Join-Path $cacheRoot 'comfy-user') + '"'),
        '--input-directory', ('"' + (Join-Path $cacheRoot 'comfy-input') + '"'),
        '--output-directory', ('"' + (Join-Path $cacheRoot 'comfy-output') + '"'))
    Start-Process -FilePath $config.comfyPython -ArgumentList $arguments -WindowStyle Hidden `
        -WorkingDirectory $config.comfyUiPath -RedirectStandardOutput (Join-Path $cacheRoot 'logs/comfy.out.log') `
        -RedirectStandardError (Join-Path $cacheRoot 'logs/comfy.err.log') | Out-Null
    Wait-Service 'http://127.0.0.1:8188/system_stats' 120
}
if (-not $SkipImages) { Write-Host "图片服务已就绪，模型：$($config.imageModel)" }
