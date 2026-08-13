param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()
function Require-Text([string]$relative, [string[]]$needles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { $failures.Add("missing file: $relative"); return }
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($needle in $needles) { if (-not $text.Contains($needle)) { $failures.Add("$relative missing contract: $needle") } }
}
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/CubeLutParser.java' @('LUT_3D_SIZE','MAX_SIZE = 65','values.size() != expected')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/CubeLutEffectFactory.java' @('SingleColorLut.createFromCube')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/MobileRenderEffects.java' @('CubeLutEffectFactory.fromPath','previewVideoEffects')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/MainActivity.java' @('setVideoEffects','lutFileOpen')
Require-Text 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/ProjectMigration.java' @('lut_path','oldVersion < 26')
Require-Text 'android-app/app/src/test/java/cn/longer233/gamenarrator/mobile/CubeLutParserTest.java' @('parsesIdentityCubeWithRedFastestOrdering','rejectsWrongEntryCountAndOneDimensionalLut')
Require-Text 'android-app/app/src/androidTest/java/cn/longer233/gamenarrator/mobile/CubeLutDeviceTest.java' @('previewAndExportUseSingleColorLutFromSameFactory')
Require-Text 'docs/ANDROID_CORE_BASELINE.json' @('"id":"visual-adjustment"','"status":"full"')
if ($failures.Count) { Write-Host "FAIL: $($failures.Count) Android LUT violation(s)"; $failures | ForEach-Object { Write-Host "  $_" }; exit 1 }
Write-Host 'PASS: Android 3D cube LUT import, persistence, shared preview/export effect and tests are wired'
