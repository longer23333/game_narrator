param([switch]$Check)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$outputPath = Join-Path $projectRoot 'docs/CONFIGURATION_REFERENCE.md'
$entries = @{}

function Add-Entry([string]$name, [string]$path, [string]$default, [string]$source) {
    if (-not $entries.ContainsKey($name)) {
        $entries[$name] = [ordered]@{ Paths = [Collections.Generic.List[string]]::new(); Defaults = [Collections.Generic.List[string]]::new(); Sources = [Collections.Generic.List[string]]::new() }
    }
    if ($path -and -not $entries[$name].Paths.Contains($path)) { $entries[$name].Paths.Add($path) }
    if ($default -and -not $entries[$name].Defaults.Contains($default)) { $entries[$name].Defaults.Add($default) }
    if (-not $entries[$name].Sources.Contains($source)) { $entries[$name].Sources.Add($source) }
}

foreach ($file in Get-ChildItem (Join-Path $projectRoot 'src/main/resources') -Filter 'application*.yml') {
    $stack = @{}
    foreach ($line in Get-Content -Encoding UTF8 $file.FullName) {
        if ($line -match '^(?<indent>\s*)(?<key>[A-Za-z0-9_-]+):') {
            $indent = $Matches.indent.Length
            @($stack.Keys | Where-Object { $_ -ge $indent }) | ForEach-Object { $stack.Remove($_) }
            $stack[$indent] = $Matches.key
        }
        $path = ($stack.Keys | Sort-Object | ForEach-Object { $stack[$_] }) -join '.'
        foreach ($match in [regex]::Matches($line, '\$\{(?<name>[A-Z][A-Z0-9_]*)(?::(?<default>[^}]*))?\}')) {
            Add-Entry $match.Groups['name'].Value $path $match.Groups['default'].Value $file.Name
        }
    }
}

# Include deployment-only settings injected directly by Java source.
foreach ($file in Get-ChildItem (Join-Path $projectRoot 'src/main/java') -Recurse -Filter '*.java') {
    $java = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    foreach ($annotation in [regex]::Matches($java, '@Value\(\s*"(?<expression>[^"]+)"\s*\)')) {
        $expression = $annotation.Groups['expression'].Value
        $propertyMatch = [regex]::Match($expression, '\$\{(?<path>[a-z][a-z0-9.-]*):')
        $path = if ($propertyMatch.Success) { $propertyMatch.Groups['path'].Value } else { 'Java @Value 注入' }
        $source = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        foreach ($match in [regex]::Matches($expression, '\$\{(?<name>[A-Z][A-Z0-9_]*)(?::(?<default>[^}]*))?\}')) {
            Add-Entry $match.Groups['name'].Value $path $match.Groups['default'].Value $source
        }
    }
}

$launcherPath = Join-Path $projectRoot 'launcher/Program.cs'
if (Test-Path $launcherPath) {
    $launcher = [IO.File]::ReadAllText($launcherPath, [Text.Encoding]::UTF8)
    foreach ($match in [regex]::Matches($launcher, 'Environment\.SetEnvironmentVariable\("(?<name>[A-Z][A-Z0-9_]*)"')) {
        Add-Entry $match.Groups['name'].Value '桌面启动器自动注入' '按设备运行时计算' 'launcher/Program.cs'
    }
}

$builder = [Text.StringBuilder]::new()
[void]$builder.AppendLine('# GameNarrator 环境变量配置参考')
[void]$builder.AppendLine()
[void]$builder.AppendLine('本文档由 `scripts/update-configuration-reference.ps1` 根据当前配置文件和桌面启动器生成。请勿手工维护表格。')
[void]$builder.AppendLine()
[void]$builder.AppendLine('## 使用原则')
[void]$builder.AppendLine()
[void]$builder.AppendLine('- 修改环境变量后需要重启后端或桌面程序；账号级 Pexels/Pixabay Key 可在后台即时修改。')
[void]$builder.AppendLine('- 标记为“敏感”的值不得提交到 Git、日志、DeepSeek 上下文或前端代码。')
[void]$builder.AppendLine('- 桌面安装版会自动设置运行目录、端口和并发参数，普通用户不需要手工配置这些项目。')
[void]$builder.AppendLine('- PostgreSQL、对象存储和云部署的操作步骤另见 `docs/POSTGRESQL_AND_CLOUD_SYNC.md`。')
[void]$builder.AppendLine()
[void]$builder.AppendLine("## 完整清单（$($entries.Count) 项）")
[void]$builder.AppendLine()
[void]$builder.AppendLine('| 环境变量 | Spring 配置路径/用途 | 默认值 | 敏感 | 来源 |')
[void]$builder.AppendLine('|---|---|---|---|---|')
foreach ($name in $entries.Keys | Sort-Object) {
    $entry = $entries[$name]
    $sensitive = if ($name -match '(PASSWORD|SECRET|API_KEY|ACCESS_KEY|TOKEN|COOKIE)') { '是' } else { '否' }
    $defaults = if ($entry.Defaults.Count) { ($entry.Defaults -join ' / ') } else { '无（必须显式设置）' }
    $escape = { param($value) ([string]$value).Replace('|', '\|').Replace("`r", ' ').Replace("`n", ' ') }
    [void]$builder.AppendLine("| ``$name`` | $(& $escape ($entry.Paths -join ' / ')) | ``$(& $escape $defaults)`` | $sensitive | $(& $escape ($entry.Sources -join ', ')) |")
}
[void]$builder.AppendLine()
[void]$builder.AppendLine('## 更新与校验')
[void]$builder.AppendLine()
[void]$builder.AppendLine('```powershell')
[void]$builder.AppendLine('.\scripts\update-configuration-reference.ps1')
[void]$builder.AppendLine('.\scripts\update-configuration-reference.ps1 -Check')
[void]$builder.AppendLine('```')

$content = $builder.ToString().Replace("`r`n", "`n")
if ($Check) {
    if (-not (Test-Path $outputPath)) { throw '配置参考文档不存在，请先运行更新脚本' }
    $actual = [IO.File]::ReadAllText($outputPath, [Text.Encoding]::UTF8).Replace("`r`n", "`n")
    if ($actual -ne $content) { throw '配置参考文档已过期，请运行 .\scripts\update-configuration-reference.ps1 后提交结果' }
    Write-Host "配置参考文档校验通过：$($entries.Count) 项环境变量。"
    return
}
[IO.File]::WriteAllText($outputPath, $content, [Text.UTF8Encoding]::new($true))
Write-Host "配置参考文档已更新：$outputPath（$($entries.Count) 项环境变量）"
