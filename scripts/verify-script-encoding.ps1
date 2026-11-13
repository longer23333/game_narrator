# 校验 scripts/ 下脚本的编码一致性。
#
# 背景：Windows PowerShell 5.1 在没有 UTF-8 BOM 时按系统 ANSI 代码页（简体中文机器为 GBK）
# 解析 .ps1。含中文注释或中文字符串的脚本会被误解码，轻则输出乱码，重则直接语法错误
# （典型报错 MissingEndCurlyBrace / UnexpectedToken，且报错行号会指向中文注释行而不是真错处）。
# 因此约定：**含非 ASCII 字符的 .ps1 必须带 UTF-8 BOM**。
#
# .cmd 不在此约束内：cmd.exe 对 UTF-8 BOM 的兼容性不稳定，可能污染首行导致 @echo off 失效。
# 含中文的 .cmd 一律靠 `chcp 65001` 自行切换代码页，此处只做提示不判失败。
#
# 用法：pwsh/powershell -File scripts\verify-script-encoding.ps1 [-Quiet]
# 退出码：0 = 通过；1 = 存在编码风险

[CmdletBinding()]
param(
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($scriptDir)) {
    $scriptDir = (Get-Location).Path
}

$bom = [byte[]](0xEF, 0xBB, 0xBF)
$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$summary = New-Object System.Collections.Generic.List[string]

function Test-HasBom {
    param([string]$Path)
    $fs = [System.IO.File]::OpenRead($Path)
    try {
        $buf = New-Object byte[] 3
        $read = $fs.Read($buf, 0, 3)
        if ($read -lt 3) { return $false }
        return ($buf[0] -eq 0xEF -and $buf[1] -eq 0xBB -and $buf[2] -eq 0xBF)
    } finally {
        $fs.Dispose()
    }
}

function Test-IsAsciiOnly {
    param([string]$Path)
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    foreach ($b in $bytes) {
        if ($b -gt 0x7F) { return $false }
    }
    return $true
}

# ---- 1. PowerShell 脚本：含非 ASCII 必须带 BOM ----
$ps1Files = @(Get-ChildItem -LiteralPath $scriptDir -Filter '*.ps1' -File | Sort-Object Name)
$ps1Ok = 0
$ps1Ascii = 0
foreach ($f in $ps1Files) {
    $hasBom = Test-HasBom -Path $f.FullName
    if (Test-IsAsciiOnly -Path $f.FullName) {
        $ps1Ascii++
        continue
    }
    if ($hasBom) {
        $ps1Ok++
    } else {
        $errors.Add("缺少 UTF-8 BOM（含中文会被 GBK 误解码）: scripts\$($f.Name)")
    }
}
$summary.Add("PowerShell: 含中文且带 BOM $ps1Ok 个 / 纯 ASCII $ps1Ascii 个 / 共 $($ps1Files.Count) 个")

# ---- 2. Python 脚本：必须是合法 UTF-8 ----
$pyFiles = @(Get-ChildItem -LiteralPath $scriptDir -Filter '*.py' -File | Sort-Object Name)
$utf8Strict = New-Object System.Text.UTF8Encoding($false, $true)
foreach ($f in $pyFiles) {
    try {
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        $null = $utf8Strict.GetString($bytes)
    } catch {
        $errors.Add("不是合法 UTF-8，Python 3 会解析失败: scripts\$($f.Name)")
    }
}
$summary.Add("Python: 检查 $($pyFiles.Count) 个文件")

# ---- 3. 批处理脚本：含中文时应有 chcp 65001（仅提示）----
$cmdFiles = @(Get-ChildItem -LiteralPath $scriptDir -Filter '*.cmd' -File | Sort-Object Name)
foreach ($f in $cmdFiles) {
    if (Test-IsAsciiOnly -Path $f.FullName) { continue }
    $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    if ($text -notmatch 'chcp\s+65001') {
        $warnings.Add("含中文但未见 chcp 65001，中文输出可能乱码: scripts\$($f.Name)")
    }
}
$summary.Add("批处理: 检查 $($cmdFiles.Count) 个文件")

# ---- 输出 ----
if (-not $Quiet) {
    foreach ($line in $summary) { Write-Host "  $line" }
    foreach ($w in $warnings) { Write-Host "  [提示] $w" -ForegroundColor Yellow }
    foreach ($e in $errors) { Write-Host "  [错误] $e" -ForegroundColor Red }
}

if ($errors.Count -gt 0) {
    if ($Quiet) {
        foreach ($e in $errors) { Write-Host "  [错误] $e" -ForegroundColor Red }
    }
    Write-Host "脚本编码校验失败：$($errors.Count) 个问题" -ForegroundColor Red
    exit 1
}

if (-not $Quiet) {
    Write-Host "脚本编码校验通过。" -ForegroundColor Green
}
exit 0
