param(
    [switch]$RequireDeviceEvidence,
    [string]$EvidenceFile = 'artifacts/android-platform-import-e2e.json'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()
function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) { if (-not $text.Contains($needle)) { $failures.Add("$relative missing contract: $needle") } }
}
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/RemoteMediaImporter.java' @('Range','Content-Range','resumeKey','contentRangeStartsAt','new FileOutputStream(part,append)')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/MainActivityPageActions.java' @('RemoteMediaImporter.download','activeDownload.cancel(true)')
Require-Text 'android-app/app/src/test/java/cn/longer233/gamenarrator/mobile/RemoteMediaImporterTest.java' @('validatesResumeRangeAndStableKey')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/BilibiliQrLogin.java' @('startDeviceConfirm','parseSetCookie')
if ($RequireDeviceEvidence) {
    $path = Join-Path $root $EvidenceFile
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $failures.Add("missing device evidence: $EvidenceFile") }
    else {
        $evidence = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) | ConvertFrom-Json
        foreach ($platform in @('bilibili','douyin','kuaishou','youtube')) {
            $row = $evidence.platforms.$platform
            if ($null -eq $row -or -not $row.login -or -not $row.resolve -or -not $row.formatSelect -or
                -not $row.cancel -or -not $row.resume -or -not $row.projectCreated) {
                $failures.Add("incomplete real-account device evidence: $platform")
            }
        }
        if ([string]::IsNullOrWhiteSpace([string]$evidence.device.serial) -or
            [string]::IsNullOrWhiteSpace([string]$evidence.device.androidVersion) -or
            [string]::IsNullOrWhiteSpace([string]$evidence.commit)) {
            $failures.Add('device evidence lacks serial, Android version or commit')
        }
    }
}
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) Android platform import violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host ('PASS: Android platform import resume contracts are wired' + $(if ($RequireDeviceEvidence) {' and four-platform real-account evidence is complete'} else {''}))
