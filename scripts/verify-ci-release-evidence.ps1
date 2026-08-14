param([string]$EvidenceFile='artifacts/ci-release-result.json',[switch]$AllowMissing)
$ErrorActionPreference='Stop';$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path;$path=Join-Path $root $EvidenceFile
if(-not(Test-Path -LiteralPath $path -PathType Leaf)){if($AllowMissing){Write-Host 'SKIP: CI evidence has not been downloaded';exit 0};throw "Missing CI evidence: $EvidenceFile"}
$e=[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8)|ConvertFrom-Json
$commit=(git -c "safe.directory=$($root.Replace('\','/'))" -C $root rev-parse HEAD).Trim();if($LASTEXITCODE-ne0){throw 'Cannot read current commit'}
$failures=[Collections.Generic.List[string]]::new();if($e.schemaVersion-ne2){$failures.Add('CI evidence schemaVersion must be 2')};if($e.status-ne'passed'){$failures.Add('CI status is not passed')};if($e.commit-ne$commit){$failures.Add("CI commit mismatch: $($e.commit) != $commit")}
foreach($gate in @('migrationBaseline','documentation','frontend','android','androidEmulator','backend','postgresqlIntegration','performanceBaseline','longVideoPerformance')){if($e.gates.$gate-ne'passed'){$failures.Add("CI gate not passed: $gate")}}
if($e.runUrl-notmatch '^https://github\.com/.+/actions/runs/\d+$'){$failures.Add('CI run URL is invalid')};try{$age=[DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse([string]$e.completedAt);if($age.TotalDays-gt7){$failures.Add('CI evidence is older than 7 days')}}catch{$failures.Add('CI completedAt is invalid')}
if($failures.Count){$failures|ForEach-Object{Write-Host "FAIL: $_"};exit 1};Write-Host 'PASS: CI evidence is fresh, same-commit and all required gates passed'
