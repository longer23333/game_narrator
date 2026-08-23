param([switch]$SkipTests, [switch]$AllowTestKey)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidRoot = Join-Path $projectRoot "android-app"
$gradle = Join-Path $androidRoot ".gradle-dist\gradle-9.5.0\bin\gradle.bat"
$buildFile = Join-Path $androidRoot "app\build.gradle"
$dist = Join-Path $projectRoot "dist"
if (-not (Test-Path -LiteralPath $gradle)) { throw "Gradle not found: $gradle" }
if (-not (Test-Path -LiteralPath $buildFile)) { throw "Android build file not found: $buildFile" }

$buildText = [IO.File]::ReadAllText($buildFile, [Text.Encoding]::UTF8)
$match = [regex]::Match($buildText, 'versionName\s*=\s*[''"]([^''"]+)[''"]')
if (-not $match.Success) { throw "Unable to read versionName from $buildFile" }
$version = $match.Groups[1].Value
$tasks = @("assembleRelease")
if (-not $SkipTests) { $tasks = @("testDebugUnitTest") + $tasks }

$requiredEnvironment = @('GN_KEYSTORE','GN_KEYSTORE_PASSWORD','GN_KEY_ALIAS','GN_KEY_PASSWORD')
$missing = @($requiredEnvironment | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missing.Count -gt 0 -and -not $AllowTestKey) {
    throw "Production signing is required. Missing environment variables: $($missing -join ', '). Use -AllowTestKey only for a non-release local audit."
}
$signingMode = if ($missing.Count -eq 0) { 'production' } else { 'test-only' }
if ($signingMode -eq 'test-only') {
    $debugKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
    if (-not (Test-Path -LiteralPath $debugKey)) { throw "Android debug keystore not found: $debugKey" }
    $env:GN_KEYSTORE = $debugKey
    $env:GN_KEYSTORE_PASSWORD = 'android'
    $env:GN_KEY_ALIAS = 'androiddebugkey'
    $env:GN_KEY_PASSWORD = 'android'
}
Write-Host "Building GameNarrator Android $version ($signingMode signing)"
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
$mappingSource = Join-Path $androidRoot 'app\build\outputs\mapping\release'
$mappingOutput = Join-Path $dist "GameNarrator-Android-$version-r8"
New-Item -ItemType Directory -Path $mappingOutput -Force | Out-Null
foreach ($name in @('mapping.txt','usage.txt','resources.txt')) {
    $source = Join-Path $mappingSource $name
    if (-not (Test-Path -LiteralPath $source)) { throw "Required R8 report missing: $source" }
    Copy-Item -LiteralPath $source -Destination (Join-Path $mappingOutput $name) -Force
}
$sdkRoots = @((Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
$localProperties = Join-Path $androidRoot 'local.properties'
if (Test-Path -LiteralPath $localProperties) {
    $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
    if ($sdkLine) { $sdkRoots += (($sdkLine.Substring(8) -replace '\\:', ':') -replace '\\\\', '\') }
}
$apksigner = $sdkRoots | ForEach-Object { Get-ChildItem (Join-Path $_ 'build-tools') -Directory -ErrorAction SilentlyContinue } |
    Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $apksigner) { throw 'apksigner was not found; cannot verify release certificate' }
$certificateOutput = & $apksigner verify --print-certs $outputApk 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $certificateOutput" }
$certificateFile = Join-Path $dist "GameNarrator-Android-$version-certificate.txt"
[IO.File]::WriteAllLines($certificateFile, @($certificateOutput), [Text.UTF8Encoding]::new($false))
$file = Get-Item -LiteralPath $outputApk
$hash = Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
$commit = git -c "safe.directory=$($projectRoot.Replace('\','/'))" -C $projectRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace([string]$commit)) { throw 'Unable to record the release commit hash' }
$manifest = [ordered]@{ version=$version; versionCode=[int]([regex]::Match($buildText,'versionCode\s*=\s*(\d+)').Groups[1].Value); commit=([string]$commit).Trim(); signingMode=$signingMode; apk=$file.Name; sha256=$hash.Hash; bytes=$file.Length; mappingDirectory=(Split-Path $mappingOutput -Leaf); certificateFile=(Split-Path $certificateFile -Leaf); createdAt=[DateTimeOffset]::Now.ToString('o') }
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $dist "GameNarrator-Android-$version-manifest.json") -Encoding UTF8
Write-Host ""
Write-Host "SUCCESS: $($file.FullName)"
Write-Host "SIZE: $([math]::Round($file.Length / 1MB, 2)) MiB"
Write-Host "SHA-256: $($hash.Hash)"
Write-Host "R8: $mappingOutput"
Write-Host "CERTIFICATE: $certificateFile"
