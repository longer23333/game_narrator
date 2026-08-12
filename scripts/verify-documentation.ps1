param([datetime]$Now = (Get-Date))
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$map = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'docs/DOCUMENTATION_SOURCE_MAP.json') | ConvertFrom-Json
$failures = [Collections.Generic.List[string]]::new()
$javaFiles = Get-ChildItem (Join-Path $root 'src/main/java') -Recurse -Filter '*.java'
$javaText = ($javaFiles | ForEach-Object { Get-Content -Raw -Encoding UTF8 $_.FullName }) -join "`n"
$reviewAge = $Now.Date - ([datetime]::ParseExact($map.lastFullReviewOn, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)).Date
if ($reviewAge.TotalDays -gt [int]$map.maximumReviewAgeDays) { $failures.Add("Full documentation review is stale ($([int]$reviewAge.TotalDays) days)") }
$actualDocuments = @(Get-ChildItem (Join-Path $root 'docs') -Recurse -Filter '*.md' | ForEach-Object { $_.FullName.Substring($root.Length + 1).Replace('\', '/') } | Sort-Object)
$reviewedDocuments = @($map.reviewedDocuments | Sort-Object)
foreach ($document in $actualDocuments) { if ($document -notin $reviewedDocuments) { $failures.Add("Document is missing from the human review manifest: $document") } }
foreach ($document in $reviewedDocuments) { if ($document -notin $actualDocuments) { $failures.Add("Reviewed document no longer exists: $document") } }
foreach ($entry in $map.documents) {
    $documentPath = Join-Path $root $entry.document
    if (-not (Test-Path -LiteralPath $documentPath -PathType Leaf)) { $failures.Add("Missing document: $($entry.document)"); continue }
    foreach ($source in $entry.sources) {
        if (-not (Test-Path -LiteralPath (Join-Path $root $source) -PathType Leaf)) { $failures.Add("Missing source mapping: $source") }
    }
    $age = $Now.Date - ([datetime]::ParseExact($entry.reviewedOn, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)).Date
    if ($age.TotalDays -gt [int]$map.maximumReviewAgeDays) { $failures.Add("Review is stale: $($entry.document) ($([int]$age.TotalDays) days)") }
    foreach ($symbol in @($entry.symbols)) {
        if ($javaText -notmatch "\b(class|interface|record|enum)\s+$([regex]::Escape($symbol))\b") { $failures.Add("Documented Java symbol is missing: $symbol") }
    }
}
Get-ChildItem (Join-Path $root 'docs') -Recurse -Filter '*.md' | ForEach-Object {
    $content = Get-Content -Raw -Encoding UTF8 $_.FullName
    foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\((?!https?://|#)(?<path>[^)#]+)(?:#[^)]+)?\)')) {
        $target = Join-Path $_.DirectoryName ([Uri]::UnescapeDataString($match.Groups['path'].Value))
        if (-not (Test-Path -LiteralPath $target)) { $failures.Add("Broken documentation link: $($_.FullName) -> $($match.Groups['path'].Value)") }
    }
}
& (Join-Path $root 'scripts/update-configuration-reference.ps1') -Check
if ($failures.Count) { throw ($failures -join [Environment]::NewLine) }
Write-Output "PASS: $($actualDocuments.Count) reviewed documents, $(@($map.documents).Count) source mappings, Java symbols, links and generated configuration reference are current"
