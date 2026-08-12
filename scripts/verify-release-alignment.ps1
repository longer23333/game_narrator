param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [Collections.Generic.List[string]]::new()

function Read-Utf8([string]$relativePath) {
    [IO.File]::ReadAllText((Join-Path $projectRoot $relativePath), [Text.Encoding]::UTF8)
}

function Require-Equal([string]$label, [string]$expected, [string]$actual) {
    if ($expected -ne $actual) { $failures.Add("$label mismatch: expected=$expected actual=$actual") }
}

[xml]$pom = Read-Utf8 'pom.xml'
$version = [string]$pom.project.version
$changelog = Read-Utf8 'release/CHANGELOG.json' | ConvertFrom-Json
$currentRelease = @($changelog.releases)[0]
Require-Equal 'changelog currentVersion' $version ([string]$changelog.currentVersion)
Require-Equal 'changelog latest release' $version ([string]$currentRelease.version)

$package = Read-Utf8 'package.json' | ConvertFrom-Json
$frontendPackage = Read-Utf8 'frontend/package.json' | ConvertFrom-Json
$frontendLockText = Read-Utf8 'frontend/package-lock.json'
Require-Equal 'root package.json' $version ([string]$package.version)
Require-Equal 'frontend package.json' $version ([string]$frontendPackage.version)
$lockVersions = [regex]::Matches($frontendLockText, '"version"\s*:\s*"([^"]+)"')
if ($lockVersions.Count -lt 2) { $failures.Add('frontend package-lock.json has no root versions') }
else {
    Require-Equal 'frontend package-lock.json' $version $lockVersions[0].Groups[1].Value
    Require-Equal 'frontend package-lock root package' $version $lockVersions[1].Groups[1].Value
}

$gradle = Read-Utf8 'android-app/app/build.gradle'
$androidVersion = [regex]::Match($gradle, "versionName\s+'([^']+)'").Groups[1].Value
$androidCode = [int][regex]::Match($gradle, 'versionCode\s+(\d+)').Groups[1].Value
Require-Equal 'Android versionName' $version $androidVersion
$parts = $version.Split('.') | ForEach-Object { [int]$_ }
$expectedCode = $parts[0] * 1000000 + $parts[1] * 1000 + $parts[2]
if ($androidCode -ne $expectedCode) { $failures.Add("Android versionCode mismatch: expected=$expectedCode actual=$androidCode") }

$launcher = Read-Utf8 'launcher/GameNarrator.Launcher.csproj'
$launcherVersion = [regex]::Match($launcher, '<Version>([^<]+)</Version>').Groups[1].Value
Require-Equal 'Windows launcher' $version $launcherVersion
foreach ($installer in @('release/installer/GameNarrator.iss','release/installer/GameNarrator-Demo-Lite.iss')) {
    $installerText = Read-Utf8 $installer
    $installerVersion = [regex]::Match($installerText, '#define AppVersion "([^"]+)"').Groups[1].Value
    Require-Equal $installer $version $installerVersion
}

$webNotes = Read-Utf8 'frontend/public/updates.js'
$webMatch = [regex]::Match($webNotes, "\{version:'([^']+)', title:'([^']+)', current:true")
if (-not $webMatch.Success) { $failures.Add('Web release notes have no current entry') }
else {
    Require-Equal 'Web release version' $version $webMatch.Groups[1].Value
    Require-Equal 'Web release title' ([string]$currentRelease.title) $webMatch.Groups[2].Value
}
$mobileNotes = Read-Utf8 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/MobileReleaseNotes.java'
$mobileMatch = [regex]::Match($mobileNotes, 'notes\.add\(new Note\("([^"]+)", "([^"]+)"')
if (-not $mobileMatch.Success) { $failures.Add('Android release notes have no first entry') }
else {
    Require-Equal 'Android release version' $version $mobileMatch.Groups[1].Value
    Require-Equal 'Android release title' ([string]$currentRelease.title) $mobileMatch.Groups[2].Value
}

$baseline = Read-Utf8 'docs/ANDROID_CORE_BASELINE.json' | ConvertFrom-Json
$weights = @{full=1.0; partial=0.5; missing=0.0}
$ids = [Collections.Generic.HashSet[string]]::new()
$score = 0.0
foreach ($capability in @($baseline.capabilities)) {
    if (-not $ids.Add([string]$capability.id)) { $failures.Add("Duplicate Android capability id: $($capability.id)") }
    if (-not $weights.ContainsKey([string]$capability.status)) { $failures.Add("Invalid Android capability status: $($capability.id)") }
    else { $score += $weights[[string]$capability.status] }
    if ([string]::IsNullOrWhiteSpace([string]$capability.evidence)) { $failures.Add("Missing Android capability evidence: $($capability.id)") }
}
$coverage = if ($ids.Count) { $score / $ids.Count } else { 0 }
if ($coverage -lt [double]$baseline.minimumCoverage) {
    $failures.Add(('Android core coverage {0:P1} is below {1:P0}' -f $coverage, [double]$baseline.minimumCoverage))
}

$workflow = Read-Utf8 '.github/workflows/release-version.yml'
if ($workflow -match '(?m)^\s*android_version:') { $failures.Add('Release workflow still accepts an independent Android versionName') }
foreach ($required in @('assembleRelease','upload-artifact','frontend','app-release')) {
    if ($workflow -notmatch [regex]::Escape($required)) { $failures.Add("Release workflow missing: $required") }
}

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) release alignment error(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host ('PASS: version={0}; Android weighted core coverage={1:P1}; Web and Android artifacts configured' -f $version, $coverage)
