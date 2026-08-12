param([switch]$Check)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$utf8 = [Text.UTF8Encoding]::new($false)
$version = 38

function Build-Baseline([string]$sourceDirectory, [string]$database) {
    $files = Get-ChildItem $sourceDirectory -Filter 'V*.sql' | Sort-Object {
        [int]([regex]::Match($_.Name, '^V(\d+)').Groups[1].Value)
    }
    $missing = 1..$version | Where-Object { $number = $_; -not ($files.Name -match "^V${number}__") }
    if ($missing) { throw "$database migrations are missing versions: $($missing -join ', ')" }
    $builder = [Text.StringBuilder]::new()
    [void]$builder.AppendLine("-- Generated $database baseline through V$version. Do not edit; run scripts/build-migration-baselines.ps1.")
    foreach ($file in $files | Where-Object { [int]([regex]::Match($_.Name, '^V(\d+)').Groups[1].Value) -le $version }) {
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("-- source: $($file.Name)")
        [void]$builder.AppendLine([IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8).Trim())
    }
    return $builder.ToString()
}

$targets = [ordered]@{
    'src/main/resources/db/baseline-h2/B38__version_2_2_4_baseline.sql' = Build-Baseline (Join-Path $root 'src/main/resources/db/migration') 'H2'
    'src/main/resources/db/baseline-postgresql/B38__version_2_2_4_baseline.sql' = Build-Baseline (Join-Path $root 'src/main/resources/db/migration-postgresql') 'PostgreSQL'
}

foreach ($entry in $targets.GetEnumerator()) {
    $path = Join-Path $root $entry.Key
    if ($Check) {
        if (-not (Test-Path $path) -or [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) -ne $entry.Value) {
            throw "Migration baseline is stale: $($entry.Key)"
        }
    } else {
        [IO.File]::WriteAllText($path, $entry.Value, $utf8)
    }
}
Write-Output "PASS: H2 and PostgreSQL baselines cover V1-V$version"
