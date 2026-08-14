param([Parameter(Mandatory=$true)][string]$FfmpegPath)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ffmpeg = (Resolve-Path -LiteralPath $FfmpegPath).Path
$previousPath = $env:PATH
try {
    $env:PATH = "$(Split-Path -Parent $ffmpeg);$previousPath"
    & (Join-Path $root 'mvnw.cmd') '-Dtest=VideoPipelineEndToEndTest#processesFiveSecondVideoThroughAllNineStagesUsingRealFfmpeg' test
    if ($LASTEXITCODE -ne 0) { throw "pipeline recovery integration exited $LASTEXITCODE" }
    Write-Output 'GN_RECOVERY_VERIFIED=1'
} finally {
    $env:PATH = $previousPath
}
