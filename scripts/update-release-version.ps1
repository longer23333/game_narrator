param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$AndroidVersion,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2100000000)][int]$AndroidVersionCode,
    [string]$Title = '稳定性与功能更新'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Read-Utf8([string]$relativePath) {
    return [IO.File]::ReadAllText((Join-Path $projectRoot $relativePath), [Text.Encoding]::UTF8)
}

function Write-Utf8([string]$relativePath, [string]$content) {
    [IO.File]::WriteAllText((Join-Path $projectRoot $relativePath), $content, $utf8)
}

function Replace-Required([string]$relativePath, [string]$pattern, [string]$replacement) {
    $content = Read-Utf8 $relativePath
    $updated = [regex]::Replace($content, $pattern, $replacement)
    if ($updated -eq $content) { throw "未在 $relativePath 中找到待更新版本" }
    Write-Utf8 $relativePath $updated
}

$pom = Read-Utf8 'pom.xml'
$match = [regex]::Match($pom, '<artifactId>game-narrator</artifactId>\s*<version>(\d+\.\d+\.\d+)</version>')
if (-not $match.Success) { throw '无法读取当前 Web/Windows 版本' }
$oldVersion = $match.Groups[1].Value
$gradle = Read-Utf8 'android-app/app/build.gradle'
$androidNameMatch = [regex]::Match($gradle, "versionName\s+'(\d+\.\d+\.\d+)'")
$androidCodeMatch = [regex]::Match($gradle, 'versionCode\s+(\d+)')
if (-not $androidNameMatch.Success -or -not $androidCodeMatch.Success) { throw '无法读取当前 Android 版本' }
$oldAndroidVersion = $androidNameMatch.Groups[1].Value
$oldAndroidCode = [int]$androidCodeMatch.Groups[1].Value
if ($AndroidVersionCode -le $oldAndroidCode) { throw "Android versionCode 必须大于 $oldAndroidCode" }

$desktopFiles = @(
    'pom.xml', 'package.json', 'frontend/package.json', 'frontend/package-lock.json',
    'frontend/index.html', 'frontend/public/app.js', 'src/main/resources/static/index.html',
    'src/main/resources/static/app.js', 'launcher/GameNarrator.Launcher.csproj',
    'release/installer/GameNarrator.iss', 'release/installer/GameNarrator-Demo-Lite.iss'
)
foreach ($file in $desktopFiles) {
    Replace-Required $file ([regex]::Escape($oldVersion)) $Version
}

foreach ($file in @('frontend/public/updates.js', 'src/main/resources/static/updates.js')) {
    $content = Read-Utf8 $file
    $content = $content -replace ', current:true', ''
    $entry = "  {version:'$Version', title:'$Title', current:true, items:['Synchronize Web, Windows and Android versions','Protect project revisions from concurrent overwrites','Create and synchronize GitHub and Gitee release tags'], jump:{view:'studio', selector:'#task-list'}},`n"
    $content = $content -replace "const releases = \[\r?\n", "const releases = [`n$entry"
    Write-Utf8 $file $content
}

Replace-Required 'android-app/app/build.gradle' "versionCode\s+$oldAndroidCode" "versionCode $AndroidVersionCode"
Replace-Required 'android-app/app/build.gradle' ([regex]::Escape("versionName '$oldAndroidVersion'")) "versionName '$AndroidVersion'"
Replace-Required 'android-app/app/src/test/java/cn/longer233/gamenarrator/mobile/MobileReleaseNotesTest.java' ([regex]::Escape('"' + $oldAndroidVersion + '"')) ('"' + $AndroidVersion + '"')
$notesPath = 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/MobileReleaseNotes.java'
$notes = Read-Utf8 $notesPath
$note = ('        notes.add(new Note("{0}", "{1}",' + "`n" +
        '                "Unified release workflow and project revision concurrency protection."));' + "`n") -f $AndroidVersion, $Title
$notes = $notes -replace '        List<Note> notes = new ArrayList<>\(\);\r?\n', "        List<Note> notes = new ArrayList<>();`n$note"
Write-Utf8 $notesPath $notes

Write-Output "Versions updated: Web/Windows $oldVersion -> $Version; Android $oldAndroidVersion ($oldAndroidCode) -> $AndroidVersion ($AndroidVersionCode)"
