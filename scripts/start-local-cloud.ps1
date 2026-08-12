[CmdletBinding()]
param(
    [switch]$StartBackend,
    [switch]$CheckOnly,
    [ValidateRange(10, 600)]
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $projectRoot 'docker-compose.postgresql.yml'

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker 命令执行失败：docker $($Arguments -join ' ')"
    }
}

function Get-ContainerHealth {
    param([Parameter(Mandatory)][string]$Service)
    $containerId = (& docker compose -f $composeFile ps -q $Service 2>$null | Select-Object -First 1)
    if (-not $containerId) { return 'missing' }
    $state = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $state) { return 'unknown' }
    return $state.Trim()
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw '未找到 Docker。请先安装并启动 Docker Desktop，然后重新运行本脚本。'
}

$ErrorActionPreference = 'SilentlyContinue'
& docker info *> $null
$dockerInfoExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($dockerInfoExitCode -ne 0) {
    throw 'Docker Desktop 尚未启动，或 Docker Engine 未就绪。请启动 Docker Desktop 后重试。'
}

Write-Host '正在校验 PostgreSQL + MinIO 部署配置……' -ForegroundColor Cyan
Invoke-Docker -Arguments @('compose', '-f', $composeFile, 'config', '--quiet')

if (-not $CheckOnly) {
    Write-Host '正在启动 PostgreSQL、MinIO 和存储桶初始化服务……' -ForegroundColor Cyan
    Invoke-Docker -Arguments @('compose', '-f', $composeFile, 'up', '-d', 'postgres', 'minio', 'minio-init')
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $postgresHealth = Get-ContainerHealth -Service 'postgres'
    $minioHealth = Get-ContainerHealth -Service 'minio'
    if ($postgresHealth -eq 'healthy' -and $minioHealth -eq 'healthy') { break }
    if ($CheckOnly) {
        throw "本地云服务尚未就绪：PostgreSQL=$postgresHealth，MinIO=$minioHealth。请去掉 -CheckOnly 启动服务。"
    }
    if ((Get-Date) -ge $deadline) {
        & docker compose -f $composeFile ps
        throw "等待服务健康状态超时（${TimeoutSeconds} 秒）：PostgreSQL=$postgresHealth，MinIO=$minioHealth。"
    }
    Write-Host "等待服务就绪：PostgreSQL=$postgresHealth，MinIO=$minioHealth"
    Start-Sleep -Seconds 2
} while ($true)

Write-Host ''
Write-Host '本地 PostgreSQL + MinIO 已就绪。' -ForegroundColor Green
Write-Host 'Navicat：127.0.0.1:5432 / 数据库 game_narrator / 用户 game_narrator / 密码 game_narrator'
Write-Host 'MinIO 控制台：http://127.0.0.1:9001 / 用户 game_narrator / 密码 change-this-local-password'
Write-Host '对象存储桶：game-narrator（私有）'

if ($StartBackend) {
    # Local Docker Compose development credentials only. Remote deployments must provide their own secrets.
    $env:POSTGRES_PASSWORD = 'game_narrator'
    $env:GAME_NARRATOR_SECRET_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='
    $env:CLOUD_SYNC_ENABLED = 'true'
    $env:OBJECT_STORAGE_ENDPOINT = 'http://127.0.0.1:9000'
    $env:OBJECT_STORAGE_REGION = 'us-east-1'
    $env:OBJECT_STORAGE_BUCKET = 'game-narrator'
    $env:OBJECT_STORAGE_ACCESS_KEY = 'game_narrator'
    $env:OBJECT_STORAGE_SECRET_KEY = 'change-this-local-password'
    Write-Host ''
    Write-Host '正在以前台方式启动后端；按 Ctrl+C 可停止后端，数据库和 MinIO 会继续运行。' -ForegroundColor Cyan
    & (Join-Path $projectRoot 'mvnw.cmd') spring-boot:run '-Dspring-boot.run.profiles=postgresql'
    exit $LASTEXITCODE
}

Write-Host '如需同时启动后端，请运行：.\scripts\start-local-cloud.ps1 -StartBackend'
