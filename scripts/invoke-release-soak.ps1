param(
    [Parameter(Mandatory=$true)][string]$FfmpegPath,
    [ValidateRange(1,1440)][int]$MinimumMinutes = 60
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ffmpeg = (Resolve-Path -LiteralPath $FfmpegPath).Path
$previousPath = $env:PATH
try {
    $env:PATH = "$(Split-Path -Parent $ffmpeg);$previousPath"
    $env:RUN_RELEASE_SOAK = 'true'
    $env:RELEASE_SOAK_MINUTES = [string]$MinimumMinutes
    & (Join-Path $root 'mvnw.cmd') '-Dtest=ReleaseSoakPerformanceIT' test
    if ($LASTEXITCODE -ne 0) { throw "release soak integration exited $LASTEXITCODE" }
    Write-Output 'GN_LONG_RUN_COMPLETED=1'
} finally {
    $env:PATH = $previousPath
    Remove-Item Env:RUN_RELEASE_SOAK -ErrorAction SilentlyContinue
    Remove-Item Env:RELEASE_SOAK_MINUTES -ErrorAction SilentlyContinue
}
