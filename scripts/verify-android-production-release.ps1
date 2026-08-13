param(
    [switch]$RequireProductionArtifacts,
    [switch]$RequireUpgradeEvidence,
    [string]$UpgradeEvidence = 'artifacts/android-upgrade-2.2.4-current.json'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()
function Require-Text([string]$relative,[string[]]$needles){$path=Join-Path $root $relative;if(-not(Test-Path -LiteralPath $path)){$failures.Add("missing file: $relative");return};$text=[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8);foreach($needle in $needles){if(-not$text.Contains($needle)){$failures.Add("$relative missing contract: $needle")}}}
Require-Text 'scripts/build-android-release-signed.ps1' @('Production signing is required','mapping.txt','apksigner','certificate.txt','manifest.json','signingMode')
Require-Text 'android-app/app/build.gradle' @('minifyEnabled true','shrinkResources true','signingConfigs')
Require-Text 'android-app/app/src/androidTest/java/cn/longer233/gamenarrator/mobile/ProductionDatabaseTest.java' @('projectTimelineAndHistorySurviveReopen','publicAssetLicenseMetadataSurvivesReopen')
Require-Text '.github/workflows/release-version.yml' @('GN_KEYSTORE_BASE64','build-android-release-signed.ps1','GameNarrator-Android-${{ inputs.version }}-r8')
if($RequireProductionArtifacts){
    $version=([regex]::Match([IO.File]::ReadAllText((Join-Path $root 'pom.xml')),'<artifactId>game-narrator</artifactId>\s*<version>([^<]+)</version>')).Groups[1].Value
    $manifestPath=Join-Path $root "dist/GameNarrator-Android-$version-manifest.json"
    if(-not(Test-Path -LiteralPath $manifestPath)){$failures.Add("missing production manifest for $version")}
    else{$manifest=[IO.File]::ReadAllText($manifestPath,[Text.Encoding]::UTF8)|ConvertFrom-Json;if($manifest.signingMode-ne'production'){$failures.Add('release artifact was not production signed')};foreach($path in @("dist/$($manifest.apk)","dist/$($manifest.mappingDirectory)/mapping.txt","dist/$($manifest.certificateFile)")){if(-not(Test-Path -LiteralPath (Join-Path $root $path))){$failures.Add("missing archived release artifact: $path")}}}
}
if($RequireUpgradeEvidence){
    $path=[IO.Path]::GetFullPath((Join-Path $root $UpgradeEvidence));$artifactRoot=[IO.Path]::GetFullPath((Join-Path $root 'artifacts'))
    if(-not $path.StartsWith($artifactRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){$failures.Add('upgrade evidence must stay under artifacts')}
    if(-not(Test-Path -LiteralPath $path)){$failures.Add("missing upgrade evidence: $UpgradeEvidence")}
    else{$e=[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8)|ConvertFrom-Json;$version=([regex]::Match([IO.File]::ReadAllText((Join-Path $root 'pom.xml')),'<artifactId>game-narrator</artifactId>\s*<version>([^<]+)</version>')).Groups[1].Value;$commit=(git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim();try{$completed=[DateTimeOffset]::Parse([string]$e.completedAt);$age=[DateTimeOffset]::Now-$completed;if($age.TotalDays-gt7-or$age.TotalMinutes-lt-5){$failures.Add('upgrade evidence completedAt is stale or in the future')}}catch{$failures.Add('upgrade evidence completedAt is invalid')};if($e.schemaVersion-ne2-or$e.fromVersion-ne'2.2.4'-or$e.toVersion-ne$version-or$e.commit-ne$commit-or-not$e.apkUpgrade-or-not$e.databaseIntegrity-or-not$e.projectIntegrity-or-not$e.mediaIntegrity-or-not$e.productionSignedApk-or[string]::IsNullOrWhiteSpace([string]$e.sessionId)-or[string]::IsNullOrWhiteSpace([string]$e.device.serial)-or[string]$e.device.serial-match'^REPLACE|^REDACTED_DEVICE_ID$'-or[string]::IsNullOrWhiteSpace([string]$e.device.model)-or[string]::IsNullOrWhiteSpace([string]$e.device.androidVersion)){$failures.Add('2.2.4 to current same-commit production device upgrade evidence is incomplete')}}
}
if($failures.Count){Write-Host "FAIL: $($failures.Count) Android production release violation(s)";$failures|ForEach-Object{Write-Host "  $_"};exit 1}
Write-Host 'PASS: Android production signing, R8 archive, certificate manifest and upgrade evidence contracts are wired'
