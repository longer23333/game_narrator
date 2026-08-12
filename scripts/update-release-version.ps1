param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [ValidateRange(0, 2100000000)][int]$AndroidVersionCode = 0,
    [string]$Title = ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('56iz5a6a5oCn5LiO5Yqf6IO95pu05paw')))
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
    if ($updated -eq $content) { throw "Version token not found in $relativePath" }
    Write-Utf8 $relativePath $updated
}

$pom = Read-Utf8 'pom.xml'
$match = [regex]::Match($pom, '<artifactId>game-narrator</artifactId>\s*<version>(\d+\.\d+\.\d+)</version>')
if (-not $match.Success) { throw 'Cannot read current Web/Windows version' }
$oldVersion = $match.Groups[1].Value
$gradle = Read-Utf8 'android-app/app/build.gradle'
$androidNameMatch = [regex]::Match($gradle, "versionName\s+'(\d+\.\d+\.\d+)'")
$androidCodeMatch = [regex]::Match($gradle, 'versionCode\s+(\d+)')
if (-not $androidNameMatch.Success -or -not $androidCodeMatch.Success) { throw 'Cannot read current Android version' }
$oldAndroidVersion = $androidNameMatch.Groups[1].Value
$oldAndroidCode = [int]$androidCodeMatch.Groups[1].Value
$AndroidVersion = $Version
if ($AndroidVersionCode -eq 0) {
    $parts = $Version.Split('.') | ForEach-Object { [int]$_ }
    $AndroidVersionCode = $parts[0] * 1000000 + $parts[1] * 1000 + $parts[2]
}
if ($AndroidVersionCode -le $oldAndroidCode) { throw "Android versionCode must be greater than $oldAndroidCode" }

$desktopFiles = @(
    'pom.xml', 'package.json', 'frontend/package.json', 'frontend/package-lock.json',
    'frontend/index.html', 'frontend/public/app.js', 'src/main/resources/static/index.html',
    'src/main/resources/static/app.js', 'launcher/GameNarrator.Launcher.csproj',
    'release/installer/GameNarrator.iss', 'release/installer/GameNarrator-Demo-Lite.iss'
)
foreach ($file in $desktopFiles) {
    Replace-Required $file ([regex]::Escape($oldVersion)) $Version
}

$releaseItems = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('WyflkIzmraXmm7TmlrAgV2Vi44CBV2luZG93cyDkuI4gQW5kcm9pZCDniYjmnKwnLCflrozmiJDmnKzniYjmnKzlip/og73kv67lpI3kuI7nqLPlrprmgKfmo4Dmn6UnLCfliJvlu7rlubblkIzmraUgR2l0SHVi44CBR2l0ZWUg5Y+R5biD5qCH562+J10=')) | ConvertFrom-Json
$changelog = Read-Utf8 'release/CHANGELOG.json' | ConvertFrom-Json
$release = [ordered]@{
    version = $Version
    title = $Title
    summary = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('57uf5LiAIFdlYuOAgVdpbmRvd3Mg5LiOIEFuZHJvaWQg54mI5pys77yM5a6M5oiQ5a+55bqU5Yqf6IO95L+u5aSN44CB56iz5a6a5oCn5qOA5p+l5LiO5Y+R5biD6K6w5b2V44CC'))
    items = @($releaseItems)
}
$changelog.currentVersion = $Version
$changelog.releases = @($release) + @($changelog.releases | Where-Object { $_.version -ne $Version })
Write-Utf8 'release/CHANGELOG.json' (($changelog | ConvertTo-Json -Depth 8) + [Environment]::NewLine)

foreach ($file in @('frontend/public/updates.js', 'src/main/resources/static/updates.js')) {
    $content = Read-Utf8 $file
    $content = $content -replace ', current:true', ''
    # Keep non-ASCII templates encoded because Windows PowerShell 5 parses UTF-8-without-BOM scripts as ANSI.
    $releaseItemsJson = ConvertTo-Json @($releaseItems) -Compress
    $entry = "  {version:'$Version', title:'$Title', current:true, items:$releaseItemsJson, jump:{view:'studio', selector:'#task-list'}}," + [Environment]::NewLine
    $content = $content -replace "const releases = \[\r?\n", ("const releases = [" + [Environment]::NewLine + $entry)
    Write-Utf8 $file $content
}

Replace-Required 'android-app/app/build.gradle' "versionCode\s+$oldAndroidCode" "versionCode $AndroidVersionCode"
Replace-Required 'android-app/app/build.gradle' ([regex]::Escape("versionName '$oldAndroidVersion'")) "versionName '$AndroidVersion'"
$notesPath = 'android-app/app/src/main/java/cn/longer233/gamenarrator/mobile/MobileReleaseNotes.java'
$notes = Read-Utf8 $notesPath
$androidReleaseBody = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('5ZCM5q2l5pu05paw5ZCE5bmz5Y+w54mI5pys77yM5a6M5oiQ5a+55bqU5Yqf6IO95L+u5aSN44CB56iz5a6a5oCn5qOA5p+l5LiO5Y+R5biD6K6w5b2V44CC'))
$note = ('        notes.add(new Note("{0}", "{1}",' + [Environment]::NewLine +
        '                "{2}"));' + [Environment]::NewLine) -f $AndroidVersion, $Title, $androidReleaseBody
$notes = $notes -replace '        List<Note> notes = new ArrayList<>\(\);\r?\n', ("        List<Note> notes = new ArrayList<>();" + [Environment]::NewLine + $note)
Write-Utf8 $notesPath $notes

Write-Output "Versions updated: Web/Windows $oldVersion -> $Version; Android $oldAndroidVersion ($oldAndroidCode) -> $AndroidVersion ($AndroidVersionCode)"
