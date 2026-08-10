param(
    [switch]$VisionOnly,
    [switch]$TextOnly
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$target = Join-Path $projectRoot "android-app\app\src\main\assets\models"
New-Item -ItemType Directory -Path $target -Force | Out-Null

$visionUrl = "https://media.githubusercontent.com/media/onnx/models/main/validated/vision/classification/mobilenet/model/mobilenetv2-7.onnx"
$textUrl = "https://hf-mirror.com/Xenova/gpt2/resolve/main/onnx/decoder_model_merged_quantized.onnx"

if (-not $TextOnly) {
    Write-Host "下载 vision-mobilenetv2.onnx（约 13.6 MiB）…"
    curl.exe -L --fail --retry 5 -o (Join-Path $target "vision-mobilenetv2.onnx") $visionUrl
    if ($LASTEXITCODE -ne 0) { throw "vision 模型下载失败" }
    curl.exe -L --fail --retry 5 -o (Join-Path $target "imagenet_labels.json") "https://cdn.jsdelivr.net/gh/anishathalye/imagenet-simple-labels@master/imagenet-simple-labels.json"
    if ($LASTEXITCODE -ne 0) { throw "ImageNet 标签下载失败" }
}
if (-not $VisionOnly) {
    Write-Host "下载 text-gpt2.onnx（约 122 MiB）…"
    curl.exe -L --fail --retry 5 -o (Join-Path $target "text-gpt2.onnx") $textUrl
    if ($LASTEXITCODE -ne 0) { throw "text 模型下载失败" }
    $tokenizerFiles = @(
        "tokenizer.json",
        "vocab.json",
        "merges.txt",
        "config.json",
        "tokenizer_config.json",
        "generation_config.json",
        "special_tokens_map.json"
    )
    foreach ($name in $tokenizerFiles) {
        Write-Host "下载 GPT-2 tokenizer: $name"
        curl.exe -L --fail --retry 5 -o (Join-Path $target $name) ("https://hf-mirror.com/Xenova/gpt2/resolve/main/" + $name)
        if ($LASTEXITCODE -ne 0) { throw "tokenizer 文件下载失败: $name" }
    }
}

Get-ChildItem $target -Filter *.onnx | Select-Object Name,Length | Format-Table -AutoSize
Write-Host "完成：ONNX 文件已放入 $target"
