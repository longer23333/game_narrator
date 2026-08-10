# Android 端侧模型部署

## 模型目录

设备端实际读取目录是应用的 `getExternalFilesDir("models")`：

```text
/storage/emulated/0/Android/data/cn.longer233.gamenarrator.mobile/files/models/
```

打包进 APK 时放在：

```text
android-app/app/src/main/assets/models/
```

首次启动会把 `assets/models/` 里的以下文件复制到设备模型目录：

- `whisper-*.bin`：Whisper.cpp 转写模型
- `whisper-cli` 或 `whisper-cli.exe`：whisper.cpp 可执行引擎（Android 真机请用 `whisper-cli`）
- `vision-*.onnx`：端侧画面语义理解模型
- `text-*.onnx`：端侧文案/分镜生成模型

## Whisper

下载 tiny 模型并直接推送到设备：

```powershell
.\scripts\install-models.ps1 -Device <serial> -Whisper
```

下载模型、推送到设备并同时复制进 `assets/models/`：

```powershell
.\scripts\install-models.ps1 -Whisper -Bundle
```

Android 版 `whisper-cli` 没有官方预编译产物，需要按设备 ABI 编译：

```powershell
.\scripts\build-android-whisper-cli.ps1 -Abi arm64-v8a
```

产物在 `dist\whisper-android\arm64-v8a\whisper-cli`，可用 `install-models.ps1 -EnginePath <路径>` 推送，或直接复制到 `assets/models/`。

## ONNX

`vision-*.onnx` 与 `text-*.onnx` 需要指定具体模型和输入输出约定，不能随便拿文件冒充可用。确认模型来源、许可证与输入输出后：

一键下载推荐模型（MobileNetV2 视觉 + GPT-2 量化文案）：

```powershell
.\scripts\fetch-onnx-models.ps1
```

只下载其中一个：

```powershell
.\scripts\fetch-onnx-models.ps1 -VisionOnly
.\scripts\fetch-onnx-models.ps1 -TextOnly
```

或手动部署：

```powershell
.\scripts\install-models.ps1 -Device <serial> -Vision -VisionPath <本地文件> -Text -TextPath <本地文件>
```

或直接放入 `assets/models/` 后重新构建 APK。
