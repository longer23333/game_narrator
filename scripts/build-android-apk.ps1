param([switch]$Release, [switch]$SkipLint, [switch]$SkipTests)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidRoot = Join-Path $projectRoot "android-app"
$gradle = Join-Path $androidRoot "gradlew.bat"
$buildFile = Join-Path $androidRoot "app\build.gradle"
$dist = Join-Path $projectRoot "dist"
if (-not (Test-Path -LiteralPath $gradle)) { throw "Gradle wrapper not found: $gradle" }
if (-not (Test-Path -LiteralPath $buildFile)) { throw "Android build file not found: $buildFile" }

$buildText = [IO.File]::ReadAllText($buildFile, [Text.Encoding]::UTF8)
$match = [regex]::Match($buildText, 'versionName\s+[''"]([^''"]+)[''"]')
if (-not $match.Success) { throw "Unable to read versionName from $buildFile" }
$version = $match.Groups[1].Value
$tasks = [Collections.Generic.List[string]]::new()
if (-not $SkipLint) { $tasks.Add("lintDebug") }
if (-not $SkipTests) { $tasks.Add("testDebugUnitTest") }
$tasks.Add($(if ($Release) { "assembleRelease" } else { "assembleDebug" }))

$variant = $(if ($Release) { "release" } else { "debug" })
Write-Host "Building GameNarrator Android $version ($variant)"
Push-Location $androidRoot
try {
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

$sourceApk = Join-Path $androidRoot "app\build\outputs\apk\$variant\app-$variant.apk"
if (-not (Test-Path -LiteralPath $sourceApk)) {
    $sourceApk = Join-Path $androidRoot "app\build\outputs\apk\$variant\app-$variant-unsigned.apk"
}
if (-not (Test-Path -LiteralPath $sourceApk)) { throw "APK not generated: $sourceApk" }
New-Item -ItemType Directory -Path $dist -Force | Out-Null
$outputName = "GameNarrator-Android-$version-$variant"
if ($sourceApk -like '*unsigned*') { $outputName += "-unsigned" }
$outputApk = Join-Path $dist "$outputName.apk"
Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force
$file = Get-Item -LiteralPath $outputApk
$hash = Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
Write-Host ""
Write-Host "SUCCESS: $($file.FullName)"
Write-Host "SIZE: $([math]::Round($file.Length / 1MB, 2)) MiB"
Write-Host "SHA-256: $($hash.Hash)"
