param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$failures = [Collections.Generic.List[string]]::new()
function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) { if (-not $text.Contains($needle)) { $failures.Add("$relative missing contract: $needle") } }
}
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/PublicAssetSearch.java' @('searchPexels','searchPixabay','Authorization')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/PublicAssetCredentials.java' @('EncryptedSharedPreferences','MasterKey','KEY_PEXELS','KEY_PIXABAY')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/PublicAssetDownloader.java' @('.part','Thread.currentThread().isInterrupted()','MAX_BYTES')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/PublicAssetRightsPolicy.java' @('BILIBILI','uploaderRightsConfirmed')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/ProjectTaskDialogController.java' @('showPublicAssetCredentials','confirmPublicAssetDownload','startPublicAssetDownload')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/ProjectMigration.java' @('source_provider','license_name','oldVersion < 27')
Require-Text 'android-app/app/src/test/java/cn/longer233/gamenarrator/mobile/PublicAssetRightsPolicyTest.java' @('bilibiliRequiresUploaderRights','removesCompletedFileWhenCatalogWriteFails')
Require-Text 'android-app/app/src/androidTest/java/cn/longer233/gamenarrator/mobile/ProductionDatabaseTest.java' @('publicAssetLicenseMetadataSurvivesReopen')
Require-Text 'docs/ANDROID_CORE_BASELINE.json' @('"id":"public-assets"','"status":"full"')
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) Android public asset violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: Android public asset credentials, search, rights confirmation, cancellable download, rollback and provenance persistence are wired'
