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

运行时统一报告三种来源状态：

- `BUNDLED`：设备文件与 APK 中同名资产的长度一致，是随包复制的版本；
- `USER_INSTALLED`：模型目录中存在文件，但 APK 没有同名资产或内容长度不同，视为用户安装/替换；
- `MISSING`：对应模型不存在。

用户可以放入不同文件名的模型，也可以用同名文件替换随包版本。推理器按文件名稳定排序选择，
诊断页会显示实际来源，不能再用笼统的“模型存在”推断它一定来自 APK。

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

或直接放入 `assets/models/` 后用 `-PbundleModels=true` 构建模型完整版 APK。标准 Release 默认排除本机模型目录，避免把未经许可证审核的模型意外打入产物，也让 CI 与本机构建内容一致。

发布前运行 `scripts/verify-android-release-audit.ps1`。它会校验高体积依赖及许可证清单、R8/资源压缩报告和标准 APK 体积上限。模型完整版使用 `-BundleModels`；清单中标为 `REVIEW_REQUIRED` 的模型必须先确认许可证，否则构建审计会失败。
