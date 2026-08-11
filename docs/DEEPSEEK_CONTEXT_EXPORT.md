# DeepSeek 项目上下文增量导出

在仓库根目录运行：

```powershell
.\scripts\update-deepseek-context.cmd
```

脚本会生成总索引 `DEEPSEEK_PROJECT_CONTEXT.md` 和 `deepseek-context/` 下的八个分卷，并在末尾输出与上次成功生成相比的变化：

- `[+]`：新增源码文件。
- `[M]`：内容或所属分卷发生变化。
- `[-]`：源码文件已删除。
- 每条变化后会显示受影响的分卷。
- `上传建议` 是本次真正需要重新上传的总索引/分卷。
- `未变化分卷（无需上传）` 可以直接跳过，无需打开文件人工比较。

首次运行会在被 Git 忽略的 `deepseek-context/.context-manifest.json` 建立 SHA-256 基线，因此第一次仍需上传全部文件。此后按源码内容哈希比较，而不是按修改时间比较。

未变化的分卷不会重新写入磁盘；纯“生成时间”变化也不会导致总索引或分卷被标记为变化。若源码没变但 Git 提交、分支或工作区状态使总索引内容变化，建议中只会列出 `DEEPSEEK_PROJECT_CONTEXT.md`。

删除 `.context-manifest.json` 会主动重建基线，并让下一次运行按首次生成处理。
