[CmdletBinding()]
param(
    [ValidateRange(10, 180)]
    [int]$TimeoutSeconds = 90,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localModelConfig = Join-Path $projectRoot 'data\config\local-models.json'
if (Test-Path -LiteralPath $localModelConfig) {
    & (Join-Path $PSScriptRoot 'start-local-models.ps1') -ConfigPath $localModelConfig
}
$logDirectory = Join-Path $projectRoot 'target\dev-logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

function Test-Http([string]$Url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing $Url -TimeoutSec 2
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
    } catch { return $false }
}

function Start-HiddenProcess([string]$File, [string[]]$Arguments, [string]$Name) {
    $stdout = Join-Path $logDirectory "$Name.out.log"
    $stderr = Join-Path $logDirectory "$Name.err.log"
    Start-Process -FilePath $File -ArgumentList $Arguments -WorkingDirectory $projectRoot `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null
}

if (-not (Test-Http 'http://127.0.0.1:8081/api/debug/health')) {
    Write-Host '正在自动启动 Spring Boot 后端……' -ForegroundColor Cyan
    Start-HiddenProcess (Join-Path $projectRoot 'mvnw.cmd') @('spring-boot:run', '"-Dspring-boot.run.jvmArguments=-Dh2.bindAddress=127.0.0.1 -Djava.net.useSystemProxies=true"') 'backend'
} else {
    if (-not (Test-Http 'http://127.0.0.1:8081/api/tasks')) {
        throw '后台已启动，但任务数据库不可用。请检查是否同时在 IDE 和启动脚本中运行了后台；本次不会重复启动服务。'
    }
    Write-Host 'Spring Boot 后端已经运行。' -ForegroundColor Green
}

if (-not (Test-Http 'http://127.0.0.1:5173/')) {
    Write-Host '正在自动启动 Vite 前端……' -ForegroundColor Cyan
    Start-HiddenProcess (Get-Command npm.cmd -ErrorAction Stop).Source @('--prefix', 'frontend', 'run', 'dev', '--', '--port', '5173', '--strictPort') 'frontend'
} else {
    Write-Host 'Vite 前端已经运行。' -ForegroundColor Green
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $backendReady = (Test-Http 'http://127.0.0.1:8081/api/debug/health') -and (Test-Http 'http://127.0.0.1:8081/api/tasks')
    $frontendReady = Test-Http 'http://127.0.0.1:5173/'
    if ($backendReady -and $frontendReady) { break }
    if ((Get-Date) -ge $deadline) {
        throw "开发环境启动超时。后端=$backendReady，前端=$frontendReady。请查看 $logDirectory"
    }
    Start-Sleep -Milliseconds 700
} while ($true)

Write-Host 'GameNarrator 开发环境已就绪。' -ForegroundColor Green
if ($NoBrowser) { exit 0 }
Write-Host '正在打开浏览器……' -ForegroundColor Cyan
Start-Process 'http://127.0.0.1:5173/'
