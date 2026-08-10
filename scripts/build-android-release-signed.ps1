param([switch]$SkipTests)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidRoot = Join-Path $projectRoot "android-app"
$gradle = Join-Path $androidRoot ".gradle-dist\gradle-9.5.0\bin\gradle.bat"
$buildFile = Join-Path $androidRoot "app\build.gradle"
$dist = Join-Path $projectRoot "dist"
if (-not (Test-Path -LiteralPath $gradle)) { throw "Gradle not found: $gradle" }
if (-not (Test-Path -LiteralPath $buildFile)) { throw "Android build file not found: $buildFile" }

$buildText = [IO.File]::ReadAllText($buildFile, [Text.Encoding]::UTF8)
$match = [regex]::Match($buildText, 'versionName\s+[''"]([^''"]+)[''"]')
if (-not $match.Success) { throw "Unable to read versionName from $buildFile" }
$version = $match.Groups[1].Value
$tasks = @("assembleRelease")
if (-not $SkipTests) { $tasks = @("testDebugUnitTest") + $tasks }

Write-Host "Building signed GameNarrator Android $version (local test keystore)"
Push-Location $androidRoot
try {
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) { throw "Android release build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

$sourceApk = Join-Path $androidRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path -LiteralPath $sourceApk)) { throw "Release APK not generated: $sourceApk" }
New-Item -ItemType Directory -Path $dist -Force | Out-Null
$outputApk = Join-Path $dist "GameNarrator-Android-$version-release-signed.apk"
Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force
$file = Get-Item -LiteralPath $outputApk
$hash = Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
Write-Host ""
Write-Host "SUCCESS: $($file.FullName)"
Write-Host "SIZE: $([math]::Round($file.Length / 1MB, 2)) MiB"
Write-Host "SHA-256: $($hash.Hash)"
