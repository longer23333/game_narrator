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
$androidVersion = [regex]::Match($gradle, "versionName\s*=\s*'([^']+)'").Groups[1].Value
$androidCode = [int][regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
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
if ([int]$baseline.schemaVersion -ne 3) { $failures.Add("Unsupported Android baseline schemaVersion: $($baseline.schemaVersion)") }
if (@('full','partial','missing') -notcontains [string]$baseline.validation.featureStatus) { $failures.Add('Android featureStatus is invalid') }
if (@('full','pending','missing') -notcontains [string]$baseline.validation.automatedValidation) { $failures.Add('Android automatedValidation is invalid') }
if ([string]$baseline.validation.physicalDeviceValidation -ne 'required') { $failures.Add('Android physicalDeviceValidation must remain required for a formal release') }
if ($baseline.validation.physicalDeviceValidated -isnot [bool]) { $failures.Add('Android physicalDeviceValidated must be an explicit boolean') }
if ($baseline.validation.productionSigningValidated -isnot [bool]) { $failures.Add('Android productionSigningValidated must be an explicit boolean') }
$testRoots = @(
    (Join-Path $projectRoot 'android-app/app/src/test/java')
    (Join-Path $projectRoot 'android-app/app/src/androidTest/java')
)
foreach ($capability in @($baseline.capabilities)) {
    $id = [string]$capability.id
    if (-not $ids.Add($id)) { $failures.Add("Duplicate Android capability id: $id") }
    if (-not $weights.ContainsKey([string]$capability.status)) { $failures.Add("Invalid Android capability status: $id") }
    else { $score += $weights[[string]$capability.status] }

    $document = [string]$capability.evidence.document
    $section = [string]$capability.evidence.section
    if ([string]::IsNullOrWhiteSpace($document) -or [string]::IsNullOrWhiteSpace($section)) {
        $failures.Add("Missing structured Android capability evidence: $id")
    } else {
        $documentPath = Join-Path $projectRoot $document
        if (-not (Test-Path -LiteralPath $documentPath -PathType Leaf)) {
            $failures.Add("Android capability evidence document not found: $id -> $document")
        } else {
            $documentText = [IO.File]::ReadAllText($documentPath, [Text.Encoding]::UTF8)
            if ($documentText -notmatch ('(?m)^\|\s*' + [regex]::Escape($section) + '\s*\|')) {
                $failures.Add("Android capability evidence section not found: $id -> $document#$section")
            }
        }
    }

    $sources = @($capability.sources)
    $tests = @($capability.tests)
    if ([string]$capability.status -ne 'missing' -and $sources.Count -eq 0) {
        $failures.Add("Android capability has no source evidence: $id")
    }
    if ([string]$capability.status -ne 'missing' -and $tests.Count -eq 0) {
        $failures.Add("Android capability has no automated test evidence: $id")
    }
    foreach ($source in $sources) {
        $relativeSource = ([string]$source).Replace('\', '/')
        if ($relativeSource -match '(^|/)(src/(test|androidTest)|build|target|deepseek-context)(/|$)') {
            $failures.Add("Android capability source evidence is not production source: $id -> $relativeSource")
        } elseif (-not (Test-Path -LiteralPath (Join-Path $projectRoot $relativeSource) -PathType Leaf)) {
            $failures.Add("Android capability source evidence not found: $id -> $relativeSource")
        }
    }
    foreach ($testReference in $tests) {
        $reference = [string]$testReference
        if ($reference -notmatch '^([A-Za-z_][A-Za-z0-9_]*)#([A-Za-z_][A-Za-z0-9_]*)$') {
            $failures.Add("Invalid Android test reference: $id -> $reference")
            continue
        }
        $className = $Matches[1]
        $methodName = $Matches[2]
        $matches = @($testRoots | ForEach-Object {
            Get-ChildItem -LiteralPath $_ -Recurse -File -Filter "$className.java" -ErrorAction SilentlyContinue
        })
        $methodPattern = '(?s)@Test\s+(?:public\s+)?void\s+' + [regex]::Escape($methodName) + '\s*\('
        $methodMatches = @($matches | Where-Object {
            [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8) -match $methodPattern
        })
        if ($methodMatches.Count -ne 1) {
            $failures.Add("Android @Test method must resolve exactly once: $id -> $reference (found $($methodMatches.Count))")
        }
    }
}
$coverage = if ($ids.Count) { $score / $ids.Count } else { 0 }
if ($coverage -lt [double]$baseline.minimumCoverage) {
    $failures.Add(('Android core coverage {0:P1} is below {1:P0}' -f $coverage, [double]$baseline.minimumCoverage))
}

$workflow = Read-Utf8 '.github/workflows/release-version.yml'
if ($workflow -match '(?m)^\s*android_version:') { $failures.Add('Release workflow still accepts an independent Android versionName') }
foreach ($required in @('verify-android-release-audit.ps1','verify-android-production-release.ps1','verify-android-platform-import.ps1 -RequireDeviceEvidence','ANDROID_DEVICE_EVIDENCE_BASE64','upload-artifact','frontend','release-signed.apk','manifest.json','certificate.txt','-r8')) {
    if ($workflow -notmatch [regex]::Escape($required)) { $failures.Add("Release workflow missing: $required") }
}
$androidReleaseAudit = Read-Utf8 'scripts/verify-android-release-audit.ps1'
if ($androidReleaseAudit -notmatch 'assembleRelease') {
    $failures.Add('Android release audit no longer builds assembleRelease')
}

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) release alignment error(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host ('PASS: version={0}; Android weighted core coverage={1:P1}; Web and Android artifacts configured' -f $version, $coverage)
