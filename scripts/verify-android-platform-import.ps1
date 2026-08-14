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
Require-Text 'android-app/app/src/test/java/cn/longer233/gamenarrator/mobile/RemoteMediaImporterTest.java' @('classifiesProtocolReplayAndFaultResponses')
Require-Text 'android-app/app/src/androidTest/java/cn/longer233/gamenarrator/mobile/RemoteMediaImporterReplayTest.java' @('downloads200WithoutContentLength','resumes206WithCorrectRange','restartsAfter416AndWrongRange','faultsRemainResumableAndAreClassified','DISCONNECT_DURING_RESPONSE_BODY','setBodyDelay')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/BilibiliQrLogin.java' @('startDeviceConfirm','parseSetCookie')
Require-Text 'android-app/app/src/test/java/cn/longer233/gamenarrator/mobile/BilibiliQrLoginTest.java' @('mapsReplayLoginStatesWithoutRealAccount')
if ($RequireDeviceEvidence) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $EvidenceFile));$artifactRoot=[IO.Path]::GetFullPath((Join-Path $root 'artifacts'))
    if(-not $path.StartsWith($artifactRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){$failures.Add('device evidence must stay under artifacts')}
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $failures.Add("missing device evidence: $EvidenceFile") }
    else {
        $evidence = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) | ConvertFrom-Json
        $commit=(git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim()
        $version=([regex]::Match([IO.File]::ReadAllText((Join-Path $root 'pom.xml')),'<artifactId>game-narrator</artifactId>\s*<version>([^<]+)</version>')).Groups[1].Value
        if($evidence.schemaVersion-ne2-or$evidence.commit-ne$commit-or$evidence.appVersion-ne$version){$failures.Add('device evidence schema, commit or app version does not match current release')}
        try{$tested=[DateTimeOffset]::Parse([string]$evidence.completedAt);$age=[DateTimeOffset]::Now-$tested;if($age.TotalDays-gt7-or$age.TotalMinutes-lt-5){$failures.Add('device evidence completedAt is stale or in the future')}}catch{$failures.Add('device evidence completedAt is invalid')}
        foreach ($platform in @('bilibili','douyin','kuaishou','youtube')) {
            $row = $evidence.platforms.$platform
            if ($null -eq $row -or -not $row.accountConfirmed -or -not $row.login -or -not $row.resolve -or -not $row.formatSelect -or
                -not $row.cancel -or -not $row.resume -or -not $row.projectCreated) {
                $failures.Add("incomplete real-account device evidence: $platform")
            }
        }
        if ([string]::IsNullOrWhiteSpace([string]$evidence.device.serial) -or [string]$evidence.device.serial -match '^REPLACE|^REDACTED_DEVICE_ID$' -or
            [string]::IsNullOrWhiteSpace([string]$evidence.device.model) -or
            [string]::IsNullOrWhiteSpace([string]$evidence.device.androidVersion) -or
            [string]::IsNullOrWhiteSpace([string]$evidence.sessionId)) {
            $failures.Add('device evidence lacks device identity or unique session')
        }
    }
}
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) Android platform import violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host ('PASS: Android platform import protocol replay and fault injection contracts are wired' + $(if ($RequireDeviceEvidence) {'; optional four-platform real-account evidence is also complete'} else {'; physical-device evidence remains optional'}))
