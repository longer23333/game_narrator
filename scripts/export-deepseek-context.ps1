param(
    [string]$OutputPath = "DEEPSEEK_PROJECT_CONTEXT.md",
    [string]$VolumeDirectory = "deepseek-context",
    [int]$MaxFileBytes = 262144,
    [switch]$Watch
)

$ErrorActionPreference = "Stop"
$scriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { $env:GAME_NARRATOR_CONTEXT_SCRIPT_ROOT }
if (-not $scriptRoot) { throw "Cannot resolve context script directory" }
$projectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$resolvedOutput = Join-Path $projectRoot $OutputPath
$resolvedVolumes = Join-Path $projectRoot $VolumeDirectory
$manifestPath = Join-Path $resolvedVolumes ".context-manifest.json"
if (-not $resolvedVolumes.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $resolvedVolumes -eq $projectRoot) { throw "VolumeDirectory must be a child of the project root" }
$utf8Bom = [System.Text.UTF8Encoding]::new($true)
$markdownFence = '```'
$allowedExtensions = @(
    ".java", ".kt", ".cs", ".js", ".ts", ".css", ".html", ".yml", ".yaml", ".sql", ".md", ".xml",
    ".json", ".gradle", ".properties", ".cmd", ".bat", ".ps1", ".csproj", ".sln", ".iss", ".toml"
)
$roots = @("src", "docs", "scripts", "frontend", "android-app", "launcher", "release", ".github")
$rootFiles = @("pom.xml", "README.md", ".gitignore", "package.json", "package-lock.json", "settings.gradle", "build.gradle", "gradle.properties")
$excludedSegments = @("target", "node_modules", ".git", ".gradle", ".gradle-dist", "build", "bin", "obj", "dist", "keystore")

function Get-RelativePath([string]$path) {
    return $path.Substring($projectRoot.Length).TrimStart("\", "/").Replace("\", "/")
}

function Test-IncludedFile([IO.FileInfo]$file) {
    $relative = Get-RelativePath $file.FullName
    if ($file.FullName -eq $resolvedOutput -or $file.FullName.StartsWith($resolvedVolumes, [StringComparison]::OrdinalIgnoreCase)) { return $false }
    if ($file.Length -gt $MaxFileBytes) { return $false }
    if ($relative -match '^src/main/resources/static/') { return $false }
    if ($relative -match '^(data|storage|tools|models|logs|cache)/') { return $false }
    if ($relative -match '^release/(staging|staging-lite|cache|desktop-publish-check)/') { return $false }
    if ($relative -match '(^|/)(local\.properties|keystore\.properties|\.env[^/]*|cookies\.txt)$') { return $false }
    $segments = $relative.Split('/')
    if ($segments | Where-Object { $excludedSegments -contains $_.ToLowerInvariant() }) { return $false }
    return $allowedExtensions -contains $file.Extension.ToLowerInvariant()
}

function Get-ContextFiles {
    $files = foreach ($root in $roots) {
        $path = Join-Path $projectRoot $root
        if (Test-Path -LiteralPath $path) {
            Get-ChildItem -LiteralPath $path -Recurse -File | Where-Object { Test-IncludedFile $_ }
        }
    }
    foreach ($name in $rootFiles) {
        $path = Join-Path $projectRoot $name
        if (Test-Path -LiteralPath $path) { Get-Item -LiteralPath $path }
    }
    return @($files | Sort-Object FullName -Unique)
}

function Get-Language([string]$extension) {
    switch ($extension.ToLowerInvariant()) {
        ".java" { "java" }; ".kt" { "kotlin" }; ".cs" { "csharp" }; ".js" { "javascript" }; ".ts" { "typescript" }
        ".css" { "css" }; ".html" { "html" }; ".yml" { "yaml" }; ".yaml" { "yaml" }; ".sql" { "sql" }
        ".xml" { "xml" }; ".json" { "json" }; ".ps1" { "powershell" }; ".cmd" { "batch" }; ".bat" { "batch" }
        ".gradle" { "groovy" }; ".properties" { "properties" }; ".toml" { "toml" }; default { "text" }
    }
}

function Get-VolumeName([string]$relative) {
    if ($relative -match '^src/test/') { return "03-tests.md" }
    if ($relative -match '^src/main/java/') { return "02-backend.md" }
    if ($relative -match '^frontend/') { return "04-frontend.md" }
    if ($relative -match '^android-app/app/src/(test|androidTest)/') { return "06-android-tests.md" }
    if ($relative -match '^android-app/') { return "05-android-main.md" }
    if ($relative -match '^(launcher|release|\.github)/' -or $relative -match '^(package|settings\.|build\.|gradle\.)') { return "07-platform-release.md" }
    if ($relative -match '^(docs|scripts)/') { return "08-docs-scripts.md" }
    return "01-foundation.md"
}

function Add-FileContent([Text.StringBuilder]$builder, [IO.FileInfo]$file) {
    $relative = Get-RelativePath $file.FullName
    $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    [void]$builder.AppendLine("## FILE: $relative")
    [void]$builder.AppendLine("- 权威级别：$(if ($relative -match '^src/main/resources/db/migration/') {'数据库迁移（最高）'} elseif ($relative -match '^src/(main|test)/|^frontend/|^android-app/|^launcher/') {'实现源码'} else {'说明/配置'})")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine($markdownFence + (Get-Language $file.Extension))
    [void]$builder.Append($content)
    if (-not $content.EndsWith("`n")) { [void]$builder.AppendLine() }
    [void]$builder.AppendLine($markdownFence)
    [void]$builder.AppendLine()
}

function Get-GitValue([string[]]$arguments, [string]$fallback = "unknown") {
    try {
        $value = (& git -C $projectRoot @arguments 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $value) { return $value }
    } catch { }
    return $fallback
}

function Get-ApiIndex([IO.FileInfo[]]$files) {
    $rows = [Collections.Generic.List[string]]::new()
    foreach ($file in $files | Where-Object { (Get-RelativePath $_.FullName) -match '^src/main/java/.+Controller\.java$' }) {
        $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        $base = if ($content -match '@RequestMapping\("([^\"]+)"\)') { $Matches[1] } else { "" }
        $matches = [regex]::Matches($content, '@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?"([^\"]*)"[^)]*\))?')
        foreach ($match in $matches) {
            $method = $match.Groups[1].Value.ToUpperInvariant()
            $path = ($base.TrimEnd('/') + "/" + $match.Groups[2].Value.TrimStart('/')).TrimEnd('/')
            if (-not $path) { $path = "/" }
            $rows.Add("| $method | ``$path`` | ``$(Get-RelativePath $file.FullName)`` |")
        }
    }
    return $rows | Sort-Object -Unique
}

function Get-PreviousManifest {
    if (-not (Test-Path -LiteralPath $manifestPath)) { return $null }
    try {
        $value = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
        if ($value.version -ne 1 -or $null -eq $value.files) { return $null }
        return $value
    } catch {
        Write-Warning "Cannot read previous context manifest; this run will create a new baseline: $($_.Exception.Message)"
        return $null
    }
}

function Get-SourceManifest([IO.FileInfo[]]$files) {
    return @($files | ForEach-Object {
        $relative = Get-RelativePath $_.FullName
        [pscustomobject]@{
            path = $relative
            volume = Get-VolumeName $relative
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    } | Sort-Object path)
}

function Compare-SourceManifest($previous, [object[]]$current) {
    $oldByPath = @{}
    if ($null -ne $previous) {
        foreach ($item in $previous.files) { $oldByPath[[string]$item.path] = $item }
    }
    $currentByPath = @{}
    foreach ($item in $current) { $currentByPath[[string]$item.path] = $item }
    $changes = [Collections.Generic.List[object]]::new()
    foreach ($item in $current) {
        $old = $oldByPath[[string]$item.path]
        if ($null -eq $old) {
            $changes.Add([pscustomobject]@{ Type = "ADDED"; Path = $item.path; Volume = $item.volume })
        } elseif ([string]$old.sha256 -ne [string]$item.sha256 -or [string]$old.volume -ne [string]$item.volume) {
            $changes.Add([pscustomobject]@{ Type = "MODIFIED"; Path = $item.path; Volume = $item.volume })
            if ([string]$old.volume -ne [string]$item.volume) {
                $changes.Add([pscustomobject]@{ Type = "MOVED_FROM"; Path = $item.path; Volume = $old.volume })
            }
        }
    }
    foreach ($old in @($oldByPath.Values)) {
        if (-not $currentByPath.ContainsKey([string]$old.path)) {
            $changes.Add([pscustomobject]@{ Type = "DELETED"; Path = $old.path; Volume = $old.volume })
        }
    }
    return @($changes | Sort-Object Volume, Path, Type)
}

function Write-Manifest([object[]]$files) {
    $manifest = [ordered]@{
        version = 1
        generatedAt = [DateTimeOffset]::Now.ToString("o")
        files = $files
    }
    $temporary = "$manifestPath.tmp"
    [IO.File]::WriteAllText($temporary, ($manifest | ConvertTo-Json -Depth 5), $utf8Bom)
    Move-Item -LiteralPath $temporary -Destination $manifestPath -Force
}

function Get-NormalizedGeneratedContent([string]$content) {
    return [regex]::Replace($content, '(?m)^> (?:自动生成|生成时间)：[^；\r\n]+；', '> 生成标记：<ignored>；')
}

function Test-WriteChangedFile([string]$path, [string]$content) {
    if (Test-Path -LiteralPath $path) {
        $previous = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
        if ((Get-NormalizedGeneratedContent $previous) -eq (Get-NormalizedGeneratedContent $content)) {
            return $false
        }
    }
    [IO.File]::WriteAllText($path, $content, $utf8Bom)
    return $true
}

function Write-ChangeSummary($previous, [object[]]$changes, [string[]]$allVolumes,
                             [string[]]$changedOutputVolumes, [bool]$indexChanged) {
    Write-Host ""
    if ($null -eq $previous) {
        Write-Host "Changes since previous export: no previous manifest; baseline created."
        Write-Host "  Upload this time: DEEPSEEK_PROJECT_CONTEXT.md and all volumes."
        return
    }
    Write-Host "Changes since previous export: $($changes.Count) source file(s)."
    foreach ($change in $changes) {
        $marker = switch ($change.Type) { "ADDED" { "+" }; "MODIFIED" { "M" }; "DELETED" { "-" }; default { ">" } }
        Write-Host "  [$marker] $($change.Path) -> $VolumeDirectory/$($change.Volume)"
    }
    $changedVolumes = @($changedOutputVolumes | Sort-Object -Unique)
    $unchangedVolumes = @($allVolumes | Where-Object { $changedVolumes -notcontains $_ } | Sort-Object)
    $upload = [Collections.Generic.List[string]]::new()
    if ($indexChanged) { $upload.Add("DEEPSEEK_PROJECT_CONTEXT.md") }
    foreach ($volume in $changedVolumes) { $upload.Add("$VolumeDirectory/$volume") }
    if ($upload.Count -eq 0) {
        Write-Host "  Upload recommendation: no source content changed; no context file needs re-uploading."
    } else {
        Write-Host "  Upload recommendation: $($upload -join ', ')."
    }
    if ($unchangedVolumes.Count -gt 0) {
        Write-Host "  Unchanged volumes (skip upload): $($unchangedVolumes -join ', ')."
    }
}

function Write-ContextBundle {
    $files = @(Get-ContextFiles)
    if (-not (Test-Path -LiteralPath $resolvedVolumes)) { New-Item -ItemType Directory -Path $resolvedVolumes | Out-Null }
    $previousManifest = Get-PreviousManifest
    $sourceManifest = @(Get-SourceManifest $files)
    $changes = @(Compare-SourceManifest $previousManifest $sourceManifest)
    $groups = $files | Group-Object { Get-VolumeName (Get-RelativePath $_.FullName) }
    $volumeStats = [Collections.Generic.List[object]]::new()
    $changedOutputVolumes = [Collections.Generic.List[string]]::new()
    foreach ($group in $groups) {
        $builder = [Text.StringBuilder]::new()
        [void]$builder.AppendLine("# GameNarrator DeepSeek 分卷：$($group.Name)")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("> 生成时间：$([DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz'))；文件数：$($group.Count)。")
        [void]$builder.AppendLine("> 判断冲突时遵循：Flyway 迁移 > 当前实现源码/配置 > 测试 > 架构与需求文档 > 生成产物。")
        [void]$builder.AppendLine()
        foreach ($file in $group.Group) { Add-FileContent $builder $file }
        $path = Join-Path $resolvedVolumes $group.Name
        if (Test-WriteChangedFile $path $builder.ToString()) { $changedOutputVolumes.Add($group.Name) }
        $volumeStats.Add([pscustomobject]@{ Name = $group.Name; Files = $group.Count; Characters = $builder.Length })
    }
    $currentVolumeNames = @($groups | Select-Object -ExpandProperty Name)
    foreach ($stale in Get-ChildItem -LiteralPath $resolvedVolumes -File -Filter "*.md") {
        if ($currentVolumeNames -notcontains $stale.Name) {
            Remove-Item -LiteralPath $stale.FullName -Force
            $changedOutputVolumes.Add($stale.Name)
        }
    }

    $status = Get-GitValue @("status", "--short") "clean or unavailable"
    $branch = Get-GitValue @("branch", "--show-current")
    $commit = Get-GitValue @("rev-parse", "--short", "HEAD")
    $apiRows = @(Get-ApiIndex $files)
    $migrations = $files | Where-Object { (Get-RelativePath $_.FullName) -match '^src/main/resources/db/migration/V' } |
        Sort-Object @{ Expression = { if ($_.BaseName -match '^V(\d+)') { [int]$Matches[1] } else { [int]::MaxValue } } }
    $configs = $files | Where-Object { $_.Extension -in @('.yml', '.yaml', '.properties', '.toml') }

    $index = [Text.StringBuilder]::new()
    [void]$index.AppendLine("# GameNarrator — DeepSeek 结构化项目索引")
    [void]$index.AppendLine()
    [void]$index.AppendLine("> 自动生成：$([DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz'))；UTF-8 BOM；索引不再内嵌全部源码。")
    [void]$index.AppendLine("> Git：分支 ``$branch``，提交 ``$commit``；收录文件 $($files.Count) 个，分卷 $($volumeStats.Count) 个。")
    [void]$index.AppendLine()
    [void]$index.AppendLine("## 使用方法")
    [void]$index.AppendLine()
    [void]$index.AppendLine("1. 始终先提供本索引。只按任务额外提供 1–3 个 ``deepseek-context/*.md`` 分卷。")
    [void]$index.AppendLine("2. 后端任务选 01+02，测试再加 03；Web 选 01+04；Android 选 01+05（测试再加 06）；Windows/发布选 01+07；需求审查选 01+08。")
    [void]$index.AppendLine("3. ``frontend/`` 是 Web 权威源码；``src/main/resources/static/`` 是 Vite 构建产物，已排除以避免重复和旧产物误导。")
    [void]$index.AppendLine("4. 判断冲突的强制优先级：**Flyway 迁移 > 当前实现源码与运行配置 > 测试 > 架构/需求文档 > 构建产物或历史说明**。")
    [void]$index.AppendLine("5. 分卷仍可能较大；复杂任务应先让模型依据本索引列出所需文件，再仅发送对应文件原文。")
    [void]$index.AppendLine()
    [void]$index.AppendLine("## 模块职责")
    [void]$index.AppendLine()
    [void]$index.AppendLine("| 模块 | 权威目录 | 职责 |")
    [void]$index.AppendLine("|---|---|---|")
    [void]$index.AppendLine("| Spring Boot | ``src/main/java`` | 任务、流水线、事件、剪辑、渲染、导入、诊断与 API |")
    [void]$index.AppendLine("| 数据库 | ``src/main/resources/db/migration`` | Flyway 追加迁移，是表结构最高权威 |")
    [void]$index.AppendLine("| Web 源码 | ``frontend`` | Vite 输入；构建后复制到 Spring static |")
    [void]$index.AppendLine("| Android | ``android-app`` | Android 客户端、Gradle、Manifest 与版本 |")
    [void]$index.AppendLine("| Windows | ``launcher``、``release`` | 桌面启动器、安装包与发布脚本 |")
    [void]$index.AppendLine("| CI/CD | ``.github`` | GitHub 自动化、构建和发布 |")
    [void]$index.AppendLine("| 测试 | ``src/test`` | 行为证据；不能替代当前实现或迁移 |")
    [void]$index.AppendLine()
    [void]$index.AppendLine("## 源码分卷")
    [void]$index.AppendLine()
    [void]$index.AppendLine("| 分卷 | 文件数 | 字符数 | 建议用途 |")
    [void]$index.AppendLine("|---|---:|---:|---|")
    $purposes = @{"01-foundation.md"="构建、配置、资源、数据库迁移";"02-backend.md"="Spring 后端实现";"03-tests.md"="Spring 测试与行为验证";"04-frontend.md"="Vite Web 源码";"05-android-main.md"="Android 实现、资源与 Gradle";"06-android-tests.md"="Android 单元及设备测试";"07-platform-release.md"="Windows、CI/CD 与发布";"08-docs-scripts.md"="需求、架构、维护脚本"}
    foreach ($item in $volumeStats | Sort-Object Name) { [void]$index.AppendLine("| ``$VolumeDirectory/$($item.Name)`` | $($item.Files) | $($item.Characters) | $($purposes[$item.Name]) |") }
    [void]$index.AppendLine()
    [void]$index.AppendLine("## 当前 Git 工作区（最近修改）")
    [void]$index.AppendLine()
    [void]$index.AppendLine($markdownFence + "text")
    [void]$index.AppendLine($status)
    [void]$index.AppendLine($markdownFence)
    [void]$index.AppendLine()
    [void]$index.AppendLine("## Flyway 迁移索引")
    [void]$index.AppendLine()
    foreach ($file in $migrations) { [void]$index.AppendLine("- ``$(Get-RelativePath $file.FullName)``") }
    [void]$index.AppendLine()
    [void]$index.AppendLine("## 运行配置索引")
    [void]$index.AppendLine()
    foreach ($file in $configs) { [void]$index.AppendLine("- ``$(Get-RelativePath $file.FullName)``") }
    [void]$index.AppendLine()
    [void]$index.AppendLine("## API 路由索引（按 Controller 注解静态提取）")
    [void]$index.AppendLine()
    [void]$index.AppendLine("| 方法 | 路径 | 来源 |")
    [void]$index.AppendLine("|---|---|---|")
    foreach ($row in $apiRows) { [void]$index.AppendLine($row) }
    [void]$index.AppendLine()
    [void]$index.AppendLine("## 安全与收录说明")
    [void]$index.AppendLine()
    [void]$index.AppendLine("- 已收录 JSON、Gradle、properties、CMD、C# 工程、Inno Setup、TOML 等文本格式。")
    [void]$index.AppendLine("- 单文件默认上限为 $MaxFileBytes bytes；大型词表、生成数据等不进入全文分卷，可用 ``-MaxFileBytes`` 显式调整。")
    [void]$index.AppendLine("- 排除 target/build/dist/bin/obj/node_modules、缓存、模型、媒体、Cookie、密钥、local.properties、环境文件及重复 static 构建产物。")
    [void]$index.AppendLine("- 本工具不修复源文件中已经存在的乱码；乱码应在权威源文件中单独修复，避免导出时猜测替换。")
    $indexChanged = Test-WriteChangedFile $resolvedOutput $index.ToString()
    Write-Manifest $sourceManifest
    Write-Host "DeepSeek structured index updated: $resolvedOutput ($($index.Length) characters)"
    foreach ($item in $volumeStats | Sort-Object Name) { Write-Host "  $($item.Name): $($item.Files) files, $($item.Characters) characters" }
    Write-ChangeSummary $previousManifest $changes @($volumeStats | Select-Object -ExpandProperty Name) `
        @($changedOutputVolumes) $indexChanged
}

Write-ContextBundle
if ($Watch) {
    Write-Host "Watching project changes. Press Ctrl+C to stop."
    while ($true) {
        Start-Sleep -Seconds 2
        $latest = (Get-ContextFiles | Measure-Object LastWriteTimeUtc -Maximum).Maximum
        if ($latest -gt (Get-Item -LiteralPath $resolvedOutput).LastWriteTimeUtc) { Write-ContextBundle }
    }
}
