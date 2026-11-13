param([switch]$SkipFrontendRestore)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$frontendRoot = Join-Path $projectRoot 'frontend'

function Invoke-Npm([string[]]$Arguments, [string]$failureMessage) {
  # Windows PowerShell promotes native stderr (including npm warnings) to ErrorRecord when redirected.
  $previousErrorAction = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & $npm @Arguments 2>&1 | ForEach-Object { Write-Host $_ }
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorAction
  }
  if ($exitCode -ne 0) { throw $failureMessage }
}

function Invoke-VerificationScript([string]$RelativePath, [string]$failureMessage) {
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $projectRoot $RelativePath)
  if ($LASTEXITCODE -ne 0) { throw $failureMessage }
}

function Test-WorkspaceViteRunning([string]$Root) {
  $escapedRoot = [Regex]::Escape((Resolve-Path $Root).Path)
  return [bool](Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -eq 'node.exe' -and $_.CommandLine -match $escapedRoot -and
    $_.CommandLine -match 'vite[\\/]bin[\\/]vite\.js'
  } | Select-Object -First 1)
}

Write-Host 'Checking generated configuration reference...'
& (Join-Path $projectRoot 'scripts\update-configuration-reference.ps1') -Check

Write-Host 'Checking release version and capability alignment...'
Invoke-VerificationScript 'scripts\verify-release-alignment.ps1' 'Release alignment failed'

Write-Host 'Checking Android guardrails...'
Invoke-VerificationScript 'scripts\verify-android-guardrails.ps1' 'Android guardrails failed'

# 含中文的 .ps1 若无 UTF-8 BOM,Windows PowerShell 5.1 会按 GBK 解析,轻则乱码重则语法错。
# 这项检查很便宜(纯字节扫描),放在构建前能避免把坏脚本提交上去。
Write-Host 'Checking script encoding...'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $projectRoot 'scripts\verify-script-encoding.ps1') -Quiet
if ($LASTEXITCODE -ne 0) { throw 'Script encoding check failed' }

if (Test-WorkspaceViteRunning $frontendRoot) {
  Write-Host 'Workspace Vite is running; keeping its node_modules unchanged during verification.'
} elseif ($SkipFrontendRestore) {
  # 守护不仅要确认 vite 存在,还要确认 esbuild/rollup 的平台二进制都在(npm ci 被打断时最先丢的就是它们)。
  $requiredFrontendFiles = @(
    'node_modules\vite\bin\vite.js',
    'node_modules\esbuild\bin\esbuild',
    'node_modules\@esbuild\win32-x64\esbuild.exe',
    'node_modules\@rollup\rollup-win32-x64-msvc\rollup.win32-x64-msvc.node'
  )
  foreach ($rel in $requiredFrontendFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot $rel) -PathType Leaf)) {
      throw "-SkipFrontendRestore requires the locked frontend dependencies to already be installed (missing: $rel)."
    }
  }
  Write-Warning 'Skipping npm ci because -SkipFrontendRestore was supplied; locked dependencies must already be installed.'
} else {
  Write-Host 'Restoring exact frontend dependencies...'
  Invoke-Npm @('--prefix', $frontendRoot, 'ci', '--no-audit', '--no-fund') 'npm ci failed'
}

Write-Host 'Building frontend...'
Invoke-Npm @('--prefix', $frontendRoot, 'run', 'build') 'Frontend build failed'

Write-Host 'Checking browser JavaScript syntax...'
$javascriptFiles = @(
  'app.js', 'asset-library.js', 'asset-tag-state.js', 'diagnostics.js', 'export.js', 'media-importer.js',
  'updates.js'
)
foreach ($name in $javascriptFiles) {
  & node --check (Join-Path $frontendRoot "public\$name")
  if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax check failed: $name" }
}

Write-Host 'Running frontend and backend tests in parallel...'
# 两套测试互不共享资源(vitest 用 frontend/,surefire 用 target/),并行可省约 1 分钟。
# 输出重定向到临时文件,结束后打印摘要;任一失败即抛错并给出日志路径。
$testLogRoot = Join-Path $env:TEMP ('game-narrator-verify-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testLogRoot | Out-Null
$npmStdOut = Join-Path $testLogRoot 'npm-stdout.log'
$npmStdErr = Join-Path $testLogRoot 'npm-stderr.log'
$mvnStdOut = Join-Path $testLogRoot 'maven-stdout.log'
$mvnStdErr = Join-Path $testLogRoot 'maven-stderr.log'
$npmCommand = '"{0}" --prefix "{1}" test' -f $npm, $frontendRoot
$mvnCommand = '"{0}" -o test' -f (Join-Path $projectRoot 'mvnw.cmd')
# Windows 环境变量不区分大小写:HTTPS_PROXY 与 https_proxy 并存会让 Start-Process
# 构建进程环境字典时抛 "已添加项"(ArgumentException)。这里删掉小写重复项再启动。
foreach ($proxyName in @('https_proxy', 'http_proxy', 'no_proxy', 'ftp_proxy', 'all_proxy')) {
  if (-not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($proxyName)) -and
      -not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($proxyName.ToUpperInvariant()))) {
    [Environment]::SetEnvironmentVariable($proxyName, $null, 'Process')
  }
}
$npmProc = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', ('"{0}"' -f $npmCommand)) `
  -WorkingDirectory $projectRoot -NoNewWindow -PassThru `
  -RedirectStandardOutput $npmStdOut -RedirectStandardError $npmStdErr
$mvnProc = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', ('"{0}"' -f $mvnCommand)) `
  -WorkingDirectory $projectRoot -NoNewWindow -PassThru `
  -RedirectStandardOutput $mvnStdOut -RedirectStandardError $mvnStdErr
# 立即取一次 Handle:.NET 竞态下,若进程退出前从未触碰 Handle,后续 .ExitCode 可能为 null,
# 导致"测试全过却被判失败"。缓存句柄后 ExitCode 一定可用。
$null = $npmProc.Handle
$null = $mvnProc.Handle
$testTimeoutMillis = 30 * 60 * 1000
if (-not $npmProc.WaitForExit($testTimeoutMillis)) { $npmProc.Kill(); throw 'Frontend tests timed out after 30 minutes' }
if (-not $mvnProc.WaitForExit($testTimeoutMillis)) { $mvnProc.Kill(); throw 'Backend tests timed out after 30 minutes' }
Write-Host ('Frontend tests exit code: ' + $npmProc.ExitCode)
Write-Host ('Backend tests exit code: ' + $mvnProc.ExitCode)
$mvnSummary = @(Select-String -LiteralPath $mvnStdOut -Pattern '^\[INFO\] Tests run:|^\[INFO\] BUILD|\[ERROR\]' | Select-Object -Last 8)
foreach ($line in $mvnSummary) { Write-Host ('  ' + $line.Line) }
if ($npmProc.ExitCode -ne 0) {
  Write-Host (Get-Content -LiteralPath $npmStdOut -Tail 40 -ErrorAction SilentlyContinue)
  Write-Host ('Full frontend test log: ' + $npmStdOut)
  throw 'Frontend behavior tests failed'
}
if ($mvnProc.ExitCode -ne 0) {
  Write-Host (Get-Content -LiteralPath $mvnStdOut -Tail 40 -ErrorAction SilentlyContinue)
  Write-Host (Get-Content -LiteralPath $mvnStdErr -Tail 20 -ErrorAction SilentlyContinue)
  Write-Host ('Full backend test log: ' + $mvnStdOut)
  throw 'Backend tests failed'
}

Write-Host 'VERIFY SUCCESS: frontend, backend, release alignment and Android guardrails passed.'
