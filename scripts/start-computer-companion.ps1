[CmdletBinding()]
param([int]$Port = 8080)

$ErrorActionPreference = 'Stop'
$bytes = New-Object byte[] 24
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$token = [Convert]::ToHexString($bytes).ToLowerInvariant()
$address = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
    Sort-Object InterfaceMetric |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $address) { throw '未找到可用的局域网 IPv4 地址' }

$env:SERVER_ADDRESS = '0.0.0.0'
$env:SERVER_PORT = $Port.ToString()
$env:GAME_NARRATOR_COMPANION_TOKEN = $token
Write-Host "电脑协同地址: http://${address}:$Port"
Write-Host "配对密钥: $token"
Write-Host '请保持此窗口运行；关闭后本次配对立即失效。'
& "$PSScriptRoot\..\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
