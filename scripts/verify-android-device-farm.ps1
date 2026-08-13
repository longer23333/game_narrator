param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workflowPath = Join-Path $root '.github\workflows\android-device-farm.yml'
$stressPath = Join-Path $root 'android-app\app\src\androidTest\assets\stress-4k.mp4'
$failures = [Collections.Generic.List[string]]::new()

if (-not (Test-Path -LiteralPath $workflowPath)) { $failures.Add('device-farm workflow is missing') }
else {
    $workflow = [IO.File]::ReadAllText($workflowPath, [Text.Encoding]::UTF8)
    foreach ($required in @('schedule:', 'workflow_dispatch:', 'google-github-actions/auth@v2',
            'gcloud firebase test android run', '-PbundleModels=true', 'FfmpegDeviceTest',
            'FullOnnxDeviceTest', 'DeviceExportSmokeTest', 'DeviceStressTest', 'retention-days: 30')) {
        if (-not $workflow.Contains($required)) { $failures.Add("workflow contract missing: $required") }
    }
    foreach ($secret in @('FIREBASE_PROJECT_ID','GOOGLE_WORKLOAD_IDENTITY_PROVIDER','GOOGLE_SERVICE_ACCOUNT')) {
        if (-not $workflow.Contains("secrets.$secret")) { $failures.Add("workflow secret wiring missing: $secret") }
    }
}
if (-not (Test-Path -LiteralPath $stressPath)) { $failures.Add('required stress-4k.mp4 fixture is missing') }
elseif ((Get-Item -LiteralPath $stressPath).Length -lt 10MB) { $failures.Add('stress-4k.mp4 is too small to be the tracked stress fixture') }

$stressTest = Join-Path $root 'android-app\app\src\androidTest\java\cn\longer233\gamenarrator\mobile\DeviceStressTest.java'
$stressText = [IO.File]::ReadAllText($stressTest, [Text.Encoding]::UTF8)
if ($stressText.Contains('Assume.assume')) { $failures.Add('4K device test must fail, not skip, when its fixture is missing') }
if (-not $stressText.Contains('>= 3840') -or -not $stressText.Contains('>= 2160')) {
    $failures.Add('4K device test must assert the fixture dimensions')
}

if ($failures.Count) {
    Write-Host "FAIL: $($failures.Count) Android device-farm contract violation(s)"
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host 'PASS: nightly physical-device gate covers arm64 FFmpeg, full ONNX, Media3 export and required 4K stress input'
