param(
    [string]$Abi = "arm64-v8a",
    [string]$Version = "v1.9.2",
    [string]$SdkRoot = ""
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ($SdkRoot) {
    $sdk = $SdkRoot
} elseif ($env:ANDROID_HOME) {
    $sdk = $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $sdk = $env:ANDROID_SDK_ROOT
} else {
    $sdk = $null
    foreach ($candidate in @((Join-Path $env:LOCALAPPDATA "Android\Sdk"), "G:\DevData\Android\Sdk")) {
        if (Test-Path (Join-Path $candidate "cmdline-tools\latest\bin\sdkmanager.bat")) {
            $sdk = $candidate
            break
        }
    }
    if (-not $sdk) {
        $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
        if ($adbCommand) { $sdk = Split-Path (Split-Path $adbCommand.Source -Parent) -Parent }
    }
}
if (-not $sdk -or -not (Test-Path $sdk)) { throw "Android SDK 未找到，请用 -SdkRoot 指定路径" }

$ndkVersion = "27.2.12479018"
$cmakeVersion = "3.22.1"
$ndk = Join-Path $sdk "ndk\$ndkVersion"
$cmakeDir = Join-Path $sdk "cmake\$cmakeVersion"
$ndkToolchain = Join-Path $ndk "build\cmake\android.toolchain.cmake"
$sdkmanager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) { throw "sdkmanager 未找到: $sdkmanager" }

if (-not (Test-Path $ndkToolchain) -or -not (Test-Path $cmakeDir)) {
    if ((Test-Path $ndk) -and (Test-Path (Join-Path $ndk ".installer"))) {
        $resolvedNdk = [IO.Path]::GetFullPath($ndk)
        $resolvedSdk = [IO.Path]::GetFullPath($sdk)
        if ($resolvedNdk.StartsWith($resolvedSdk, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $ndk -Recurse -Force
        }
    }
    Write-Host "安装 NDK $ndkVersion 与 CMake $cmakeVersion（首次需要几分钟）…"
    "y`n" * 20 | & $sdkmanager --licenses | Out-Null
    & $sdkmanager "ndk;$ndkVersion" "cmake;$cmakeVersion"
    if ($LASTEXITCODE -ne 0) { throw "NDK/CMake 安装失败" }
    if (-not (Test-Path $ndkToolchain)) { throw "NDK 安装后工具链仍不存在: $ndkToolchain" }
}

$cmake = Join-Path $cmakeDir "bin\cmake.exe"
if (-not (Test-Path $cmake)) { throw "cmake 未找到: $cmake" }

$sourceDir = Join-Path $projectRoot "tools\whisper-android"
if (-not (Test-Path (Join-Path $sourceDir "CMakeLists.txt"))) {
    git clone --depth 1 --branch $Version https://github.com/ggerganov/whisper.cpp.git $sourceDir
    if ($LASTEXITCODE -ne 0) { throw "whisper.cpp 克隆失败" }
}

$buildDir = Join-Path $sourceDir "build-android-$Abi"
$resolvedBuild = [IO.Path]::GetFullPath($buildDir)
$resolvedSource = [IO.Path]::GetFullPath($sourceDir)
if (-not $resolvedBuild.StartsWith($resolvedSource, [StringComparison]::OrdinalIgnoreCase)) {
    throw "拒绝清理工作区外目录: $resolvedBuild"
}
if (Test-Path (Join-Path $buildDir "CMakeCache.txt")) {
    Remove-Item -LiteralPath $buildDir -Recurse -Force
}
if (-not (Test-Path $ndkToolchain)) { throw "NDK 工具链未找到: $ndkToolchain" }
$ninja = Join-Path $cmakeDir "bin\ninja.exe"
if (-not (Test-Path $ninja)) { throw "Ninja 未找到: $ninja" }
& $cmake -B $buildDir -S $sourceDir -G Ninja `
    "-DCMAKE_TOOLCHAIN_FILE=$ndkToolchain" `
    "-DANDROID_ABI=$Abi" `
    "-DANDROID_PLATFORM=android-29" `
    "-DCMAKE_BUILD_TYPE=Release" `
    "-DCMAKE_MAKE_PROGRAM=$ninja"
if ($LASTEXITCODE -ne 0) { throw "CMake 配置失败" }
& $cmake --build $buildDir --target whisper-cli --config Release
if ($LASTEXITCODE -ne 0) { throw "whisper-cli 编译失败" }

$candidates = @(
    (Join-Path $buildDir "bin\whisper-cli"),
    (Join-Path $buildDir "Release\whisper-cli.exe"),
    (Join-Path $buildDir "whisper-cli")
)
$binary = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $binary) { throw "未找到编译产物: $buildDir" }

$outDir = Join-Path $projectRoot "dist\whisper-android\$Abi"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$outBinary = Join-Path $outDir "whisper-cli"
Copy-Item -LiteralPath $binary -Destination $outBinary -Force
Write-Host "SUCCESS: $outBinary"
Write-Host "下一步：把 whisper-*.bin 与 whisper-cli 放进 android-app/app/src/main/assets/models/，或 adb push 到设备模型目录。"
