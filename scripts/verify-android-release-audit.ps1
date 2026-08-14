param([switch]$BundleModels, [switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$androidRoot = Join-Path $projectRoot 'android-app'
$manifest = Get-Content (Join-Path $projectRoot 'docs/ANDROID_RELEASE_AUDIT.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$buildFile = Get-Content (Join-Path $androidRoot 'app/build.gradle') -Raw -Encoding UTF8

foreach ($dependency in $manifest.requiredDependencies) {
    if (-not $buildFile.Contains("'$($dependency.coordinate)'")) {
        throw "Android dependency audit failed: missing or changed $($dependency.coordinate)"
    }
    if ([string]::IsNullOrWhiteSpace($dependency.license)) {
        throw "Android license audit failed: $($dependency.coordinate) has no license"
    }
}

if ($BundleModels) {
    $modelRoot = Join-Path $androidRoot 'app/src/main/assets/models'
    foreach ($model in $manifest.bundledModelSources) {
        $matches = @(Get-ChildItem $modelRoot -Filter $model.pattern -File -ErrorAction SilentlyContinue)
        if ($matches.Count -gt 0 -and $model.license -eq 'REVIEW_REQUIRED') {
            throw "Android model license audit failed: $($model.pattern) requires an approved license before bundling"
        }
    }
}

if (-not $SkipBuild) {
    $gradle = Join-Path $androidRoot $(if ($IsLinux -or $IsMacOS) { 'gradlew' } else { 'gradlew.bat' })
    $arguments = @('-p', $androidRoot, 'clean', 'assembleRelease')
    if ($BundleModels) { $arguments += '-PbundleModels=true' }
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Android release build failed' }
}

$releaseRoot = Join-Path $androidRoot 'app/build/outputs/apk/release'
$apkCandidates = @(@('app-release.apk', 'app-release-unsigned.apk') | ForEach-Object { Join-Path $releaseRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
if ($apkCandidates.Count -ne 1) { throw "Android release audit requires exactly one signed or unsigned APK under: $releaseRoot" }
$apk = $apkCandidates[0]
$apkBytes = (Get-Item -LiteralPath $apk).Length
$limit = if ($BundleModels) { [long]$manifest.apkLimits.bundledModelsReleaseBytes } else { [long]$manifest.apkLimits.standardReleaseBytes }
if ($apkBytes -gt $limit) { throw "Android APK size regression: $apkBytes bytes exceeds $limit bytes" }

$mappingRoot = Join-Path $androidRoot 'app/build/outputs/mapping/release'
foreach ($name in @('mapping.txt', 'usage.txt', 'resources.txt')) {
    $report = Join-Path $mappingRoot $name
    if (-not (Test-Path -LiteralPath $report) -or (Get-Item -LiteralPath $report).Length -eq 0) {
        throw "Android release shrink audit failed: missing or empty $name"
    }
}

$mode = if ($BundleModels) { 'bundled-models' } else { 'standard' }
Write-Host "PASS: Android $mode release=$apkBytes bytes; limit=$limit; dependencies, licenses and shrink reports verified"
