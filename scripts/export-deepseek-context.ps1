param(
    [string]$OutputPath = "DEEPSEEK_PROJECT_CONTEXT.md",
    [switch]$Watch
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedOutput = Join-Path $projectRoot $OutputPath
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Get-ContextFiles {
    $roots = @("src", "docs", "scripts")
    $extensions = @(".java", ".js", ".css", ".html", ".yml", ".yaml", ".sql", ".md", ".xml", ".ps1")
    $files = foreach ($root in $roots) {
        $absoluteRoot = Join-Path $projectRoot $root
        if (Test-Path -LiteralPath $absoluteRoot) {
            Get-ChildItem -LiteralPath $absoluteRoot -Recurse -File | Where-Object {
                $extensions -contains $_.Extension.ToLowerInvariant() -and
                $_.FullName -ne $resolvedOutput
            }
        }
    }
    foreach ($name in @("pom.xml", "README.md", ".gitignore")) {
        $path = Join-Path $projectRoot $name
        if (Test-Path -LiteralPath $path) { Get-Item -LiteralPath $path }
    }
    $files | Sort-Object FullName -Unique
}

function Get-Language([string]$extension) {
    switch ($extension.ToLowerInvariant()) {
        ".java" { "java" }
        ".js" { "javascript" }
        ".css" { "css" }
        ".html" { "html" }
        ".yml" { "yaml" }
        ".yaml" { "yaml" }
        ".sql" { "sql" }
        ".xml" { "xml" }
        ".ps1" { "powershell" }
        default { "text" }
    }
}

function Write-ContextBundle {
    $files = @(Get-ContextFiles)
    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.AppendLine("# GameNarrator — DeepSeek 项目上下文包")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("> 自动生成时间：$([DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz'))")
    [void]$builder.AppendLine("> 文件数量：$($files.Count)。本文件由 scripts/export-deepseek-context.ps1 生成，请勿手工维护生成区。")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("## 给 DeepSeek 的强制工作规则")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine(@'
你正在维护一个真实可运行的 Java 21 / Spring Boot 3.2 项目。请先阅读本文件中的项目约束、目录清单和相关源码，再回答或修改。用户的新要求优先于旧文档，但不得擅自扩大范围。

1. 不要虚构不存在的类、接口、API、配置、依赖或测试结果；结论必须能由下方源码验证。
2. 修改应复用现有分层和命名，保持后端、前端、配置、数据库迁移、诊断和测试一致。
3. 平台、浏览器、模型、路径、模板和供应商能力必须配置化，不写死单个平台或本机环境。
4. 不得绕过网站认证、DRM、付费墙或浏览器凭据保护；媒体导入仅处理用户有权使用的内容。
5. 不输出整文件替换，除非用户明确要求；优先给出可应用的 unified diff，并列出受影响文件。
6. 不覆盖无关改动，不删除数据，不使用破坏性 Git 命令。数据库变更必须新增 Flyway 迁移，不能修改已执行迁移。
7. 外部进程必须处理并发输出、超时、中断、退出码、残留进程和安全路径；错误信息不得泄露 Cookie、Token 或完整命令中的秘密。
8. 前端轮询不得覆盖用户正在编辑的表单、重置视频播放位置或反复重建已打开的详情视图。
9. 任务重试应保留已完成阶段；取消应终止实际工作；缓存清理应限制在授权存储目录并可解释清理范围。
10. 修改完成后至少运行 `.\mvnw.cmd test`，报告真实的测试数量与失败原因；不能声称未执行的验证已通过。
11. 若参考 GitHub/Gitee 项目，只借鉴架构和可靠性做法，检查许可证与版本差异，不直接复制不兼容代码。
12. 回答使用简洁中文，先说结果，再说改动文件、验证结果、剩余风险和启动/迁移要求。
'@)
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("## 当前文件清单")
    [void]$builder.AppendLine()
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length).TrimStart("\").Replace("\", "/")
        [void]$builder.AppendLine("- ``$relative``（$($file.Length) bytes）")
    }
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("## 当前项目原文")
    [void]$builder.AppendLine()
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length).TrimStart("\").Replace("\", "/")
        $language = Get-Language $file.Extension
        $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        [void]$builder.AppendLine("### FILE: $relative")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("````$language")
        [void]$builder.Append($content)
        if (-not $content.EndsWith("`n")) { [void]$builder.AppendLine() }
        [void]$builder.AppendLine("````")
        [void]$builder.AppendLine()
    }
    [IO.File]::WriteAllText($resolvedOutput, $builder.ToString(), $utf8NoBom)
    Write-Host "DeepSeek context updated: $resolvedOutput ($($builder.Length) characters)"
}

Write-ContextBundle
if ($Watch) {
    Write-Host "Watching project changes. Press Ctrl+C to stop."
    while ($true) {
        Start-Sleep -Seconds 2
        $latest = (Get-ContextFiles | Measure-Object LastWriteTimeUtc -Maximum).Maximum
        if ($latest -gt (Get-Item -LiteralPath $resolvedOutput).LastWriteTimeUtc) {
            Write-ContextBundle
        }
    }
}
