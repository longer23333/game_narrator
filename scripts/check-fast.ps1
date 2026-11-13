[CmdletBinding()]
param(
    [switch]$Full,
    [switch]$ChangedTests
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gitSafeRoot = $projectRoot.Replace('\', '/')
Set-Location $projectRoot

function Invoke-Checked([string]$Label, [scriptblock]$Action) {
    Write-Host "[check] $Label" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit code $LASTEXITCODE)" }
}

if ($Full) {
    Invoke-Checked 'Backend full test suite' { & (Join-Path $projectRoot 'mvnw.cmd') test }
    Invoke-Checked 'Frontend tests' { & npm.cmd --prefix frontend test }
    Invoke-Checked 'Frontend production build' { & npm.cmd --prefix frontend run build }
    Invoke-Checked 'Desktop launcher build' { & dotnet build launcher\GameNarrator.Launcher.csproj }
    Invoke-Checked 'Git whitespace check' { & git -c "safe.directory=$gitSafeRoot" diff --check }
    Write-Host 'Full checks passed.' -ForegroundColor Green
    exit 0
}

$tracked = @(& git -c "safe.directory=$gitSafeRoot" diff --name-only HEAD)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read tracked Git changes' }
$untracked = @(& git -c "safe.directory=$gitSafeRoot" ls-files --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read untracked Git files' }
$changed = @($tracked + $untracked | Where-Object { $_ } | Sort-Object -Unique)
if ($changed.Count -eq 0) {
    Write-Host 'No code changes detected.' -ForegroundColor Green
    exit 0
}

$backendChanged = @($changed | Where-Object {
    $_ -eq 'pom.xml' -or $_ -like 'src/main/*' -or $_ -like 'src/test/*'
}).Count -gt 0
$frontendChanged = @($changed | Where-Object { $_ -like 'frontend/*' }).Count -gt 0
$launcherChanged = @($changed | Where-Object { $_ -like 'launcher/*' }).Count -gt 0
$powershellFiles = @($changed | Where-Object { $_ -like '*.ps1' -and (Test-Path -LiteralPath $_) })

if ($backendChanged) {
    Invoke-Checked 'Backend incremental compile (including test sources)' {
        & (Join-Path $projectRoot 'mvnw.cmd') -DskipTests test-compile
    }
    if ($ChangedTests) {
        $testClasses = @($changed | Where-Object {
            $_ -like 'src/test/java/*Test.java' -and (Test-Path -LiteralPath $_)
        } |
            ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) } | Sort-Object -Unique)
        if ($testClasses.Count -gt 0) {
            $selection = $testClasses -join ','
            Invoke-Checked "Changed backend tests ($($testClasses.Count) classes)" {
                & (Join-Path $projectRoot 'mvnw.cmd') "-Dtest=$selection" test
            }
        }
    }
}

if ($frontendChanged) {
    $javascriptFiles = @($changed | Where-Object {
        ($_ -like 'frontend/*.js' -or $_ -like 'frontend/**/*.js') -and (Test-Path -LiteralPath $_)
    })
    foreach ($file in $javascriptFiles) {
        Invoke-Checked "JavaScript syntax: $file" { & node --check $file }
    }
    if (@($changed | Where-Object {
        $_ -like 'frontend/src/*' -or $_ -like 'frontend/test/*' -or
        $_ -eq 'frontend/package.json' -or $_ -eq 'frontend/package-lock.json' -or
        $_ -like 'frontend/*config.*'
    }).Count -gt 0) {
        Invoke-Checked 'Frontend fast tests' { & npm.cmd --prefix frontend test }
    }
}

if ($launcherChanged) {
    Invoke-Checked 'Desktop launcher incremental build' { & dotnet build launcher\GameNarrator.Launcher.csproj --no-restore }
}

foreach ($file in $powershellFiles) {
    Write-Host "[check] PowerShell syntax: $file" -ForegroundColor Cyan
    $tokens = $null; $errors = $null
    $scriptText = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $file).Path, [Text.Encoding]::UTF8)
    [void][Management.Automation.Language.Parser]::ParseInput(
        $scriptText, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) { throw ($errors | ForEach-Object Message | Out-String) }
}

Invoke-Checked 'Git whitespace check' { & git -c "safe.directory=$gitSafeRoot" diff --check }
Write-Host 'Fast checks passed. Before release or merge run: .\scripts\check-fast.ps1 -Full' -ForegroundColor Green
